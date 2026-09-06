package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.Replier;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.service.BangumiWorkTranslationService;
import com.github.anicmv.telegrambot.service.TraceMoeService;
import com.github.anicmv.telegrambot.service.TraceMoeService.AnimeResult;
import com.github.anicmv.telegrambot.service.TraceMoeService.SearchResponse;
import com.github.anicmv.telegrambot.utils.BotUtil;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;

/**
 * @author anicmv
 * @date 2026/9/5
 * @description /anime 动漫识图命令：回复一张图片发送 /anime，识别动漫场景。
 */
@Log4j2
@BotCommand(value = BotConstant.CMD_ANIME, description = "动漫识图：回复一张图片发送 /anime")
@Component
public class AnimeSearchCommandHandler implements BotCommandHandler {

    private static final int TELEGRAM_VIDEO_CAPTION_LIMIT = 1024;
    private static final String RESULT_HEADER = "<b>🎬 动漫识图结果</b>\n\n";
    private static final String RESULT_FOOTER = "Powered by <a href=\"https://trace.moe\">trace.moe</a>";

    private final Messenger messenger;
    private final TraceMoeService traceMoeService;
    private final BangumiWorkTranslationService bangumiWorkTranslationService;
    private final TaskExecutor botBackgroundExecutor;

    public AnimeSearchCommandHandler(Messenger messenger,
                                     TraceMoeService traceMoeService,
                                     BangumiWorkTranslationService bangumiWorkTranslationService,
                                     @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor) {
        this.messenger = messenger;
        this.traceMoeService = traceMoeService;
        this.bangumiWorkTranslationService = bangumiWorkTranslationService;
        this.botBackgroundExecutor = botBackgroundExecutor;
    }

    @Override
    public void execute(BotContext context) {
        Message commandMessage = context.message();
        Message repliedMessage = commandMessage == null ? null : commandMessage.getReplyToMessage();
        if (repliedMessage == null || !repliedMessage.hasPhoto()) {
            Replier.of(context, messenger).text("请回复一张动漫截图后发送 /anime");
            return;
        }

        List<PhotoSize> photos = repliedMessage.getPhoto();
        if (photos == null || photos.isEmpty()) {
            Replier.of(context, messenger).text("未找到图片");
            return;
        }

        String fileId = photos.getLast().getFileId();
        try {
            botBackgroundExecutor.execute(() -> searchAndReply(context, fileId));
        } catch (RejectedExecutionException e) {
            log.warn("动漫识图任务被拒绝: chatId={}", context.chatId());
            Replier.of(context, messenger).text("当前识图任务较多，请稍后重试");
        }
    }

    private void searchAndReply(BotContext context, String fileId) {
        Replier replier = Replier.of(context, messenger);
        Integer progressMessageId = replier.htmlAndReturnId("<b>🔍 正在识别动漫场景...</b>");
        try {
            byte[] imageBytes = messenger.downloadFileBytes(fileId);
            if (imageBytes == null || imageBytes.length == 0) {
                replyOrEdit(replier, progressMessageId, "<b>❌ 获取图片失败，请稍后重试</b>");
                return;
            }

            SearchResponse response = traceMoeService.search(imageBytes);
            if (!response.success() || response.results().isEmpty()) {
                replyOrEdit(replier, progressMessageId,
                        "<b>❌ 未找到结果</b>\n" + BotUtil.escapeHtml(response.message()));
                return;
            }

            if (progressMessageId != null) {
                replier.editHtml(progressMessageId, "<b>🌐 正在翻译标题...</b>");
            }
            Map<String, BangumiWorkTranslationService.Translation> titleTranslations =
                    translateTitles(response.results());
            String resultText = buildResultText(response.results(), titleTranslations);
            if (sendPreviewVideo(replier, progressMessageId, response.results(), titleTranslations)) {
                replier.deleteSilently(progressMessageId);
            } else {
                replyOrEdit(replier, progressMessageId, resultText);
            }
            log.info("动漫识图完成: chatId={}, resultCount={}", context.chatId(), response.results().size());
        } catch (Exception e) {
            log.warn("动漫识图失败: chatId={}", context.chatId(), e);
            replyOrEdit(replier, progressMessageId, "<b>❌ 识图失败，请稍后重试</b>");
        }
    }

    private boolean sendPreviewVideo(Replier replier, Integer progressMessageId, List<AnimeResult> results,
                                     Map<String, BangumiWorkTranslationService.Translation> titleTranslations) {
        Optional<String> caption = buildVideoCaption(results, titleTranslations);
        AnimeResult bestResult = highestSimilarityResult(results);
        if (caption.isEmpty() || bestResult.previewUrl() == null || bestResult.previewUrl().isBlank()) {
            return false;
        }

        Path previewFile = null;
        try {
            previewFile = traceMoeService.downloadPreview(bestResult.previewUrl());
            replier.editHtml(progressMessageId, "<b>📤 正在上传预览片段...</b>");
            return replier.videoByPath(previewFile.toString(), caption.get());
        } catch (Exception e) {
            log.warn("动漫预览片段发送失败: previewUrl={}", bestResult.previewUrl(), e);
            return false;
        } finally {
            BotUtil.deleteQuietly(previewFile);
        }
    }

    private AnimeResult highestSimilarityResult(List<AnimeResult> results) {
        return results.stream()
                .max(Comparator.comparingDouble(AnimeResult::similarity))
                .orElseThrow();
    }

    private Map<String, BangumiWorkTranslationService.Translation> translateTitles(List<AnimeResult> results) {
        try {
            List<String> titles = results.stream().map(this::displayTitle).toList();
            return bangumiWorkTranslationService.translateTitlesAsync(titles).join();
        } catch (RuntimeException e) {
            log.warn("Bangumi 标题翻译失败", e);
            return Map.of();
        }
    }

    private String buildResultText(List<AnimeResult> results,
                                   Map<String, BangumiWorkTranslationService.Translation> titleTranslations) {
        StringBuilder text = new StringBuilder(RESULT_HEADER);
        for (int i = 0; i < results.size(); i++) {
            text.append(buildResultItem(i + 1, results.get(i), titleTranslations));
        }
        return text.append(RESULT_FOOTER).toString();
    }

    private Optional<String> buildVideoCaption(List<AnimeResult> results,
                                               Map<String, BangumiWorkTranslationService.Translation> titleTranslations) {
        List<AnimeResult> rankedResults = results.stream()
                .sorted(Comparator.comparingDouble(AnimeResult::similarity).reversed())
                .toList();
        for (int included = rankedResults.size(); included >= 1; included--) {
            StringBuilder caption = new StringBuilder(RESULT_HEADER);
            for (int i = 0; i < included; i++) {
                caption.append(buildResultItem(i + 1, rankedResults.get(i), titleTranslations));
            }
            int omitted = rankedResults.size() - included;
            if (omitted > 0) {
                caption.append("另有 ").append(omitted).append(" 条结果因消息长度限制未显示\n\n");
            }
            caption.append(RESULT_FOOTER);
            if (caption.length() <= TELEGRAM_VIDEO_CAPTION_LIMIT) {
                return Optional.of(caption.toString());
            }
        }
        return Optional.empty();
    }

    private String buildResultItem(int index, AnimeResult result,
                                   Map<String, BangumiWorkTranslationService.Translation> titleTranslations) {
        StringBuilder text = new StringBuilder();
        String title = displayTitle(result);
        BangumiWorkTranslationService.Translation translated = titleTranslations.get(title);
        String header = translated == null || translated.nameCn().isBlank()
                ? BotUtil.escapeHtml(title)
                : translated.toHtmlLink() + "（" + BotUtil.escapeHtml(title) + "）";
        text.append("<b>").append(index).append(". ")
                .append(header).append("</b>\n");
        appendAlternativeTitle(text, result);
        text.append("📺 ").append(BotUtil.escapeHtml(result.episode())).append("\n")
                .append("⏱ 时间戳：").append(result.formatTimestamp()).append("\n")
                .append("🎯 相似度：").append(result.formatSimilarity()).append("\n");
        if (result.previewUrl() != null && !result.previewUrl().isBlank()) {
            text.append("🔗 <a href=\"")
                    .append(BotUtil.escapeHtml(result.previewUrl()))
                    .append("\">预览片段</a>\n");
        }
        return text.append("\n").toString();
    }

    private String displayTitle(AnimeResult result) {
        if (result.titleNative() != null && !result.titleNative().isBlank()) {
            return result.titleNative();
        }
        if (result.title() != null && !result.title().isBlank()) {
            return result.title();
        }
        if (result.titleEnglish() != null && !result.titleEnglish().isBlank()) {
            return result.titleEnglish();
        }
        return "未知作品";
    }

    private void appendAlternativeTitle(StringBuilder text, AnimeResult result) {
        String title = result.title();
        if (title != null && !title.isBlank() && !title.equals(displayTitle(result))) {
            text.append(BotUtil.escapeHtml(title)).append("\n");
        } else if (result.titleEnglish() != null && !result.titleEnglish().isBlank()
                && !result.titleEnglish().equals(displayTitle(result))) {
            text.append(BotUtil.escapeHtml(result.titleEnglish())).append("\n");
        }
    }

    private void replyOrEdit(Replier replier, Integer messageId, String html) {
        if (messageId == null) {
            replier.html(html);
        } else {
            replier.editHtml(messageId, html);
        }
    }
}
