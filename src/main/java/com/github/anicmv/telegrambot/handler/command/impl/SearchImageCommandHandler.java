package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.service.SauceNaoService;
import com.github.anicmv.telegrambot.service.SauceNaoService.SearchResponse;
import com.github.anicmv.telegrambot.service.SauceNaoService.SearchResult;
import com.github.anicmv.telegrambot.utils.BotUtil;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.Replier;
import com.github.anicmv.telegrambot.model.BotContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /searchimg 以图搜图命令处理器，使用 SauceNAO 搜索图片来源。
 */
@Log4j2
@BotCommand(value = BotConstant.CMD_SEARCHIMG, description = "以图搜图，格式：/searchimg 图片链接")
@Component
public class SearchImageCommandHandler implements BotCommandHandler {

    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "https?://[^\\s]+\\.(?:jpg|jpeg|png|gif|webp|bmp|svg)", Pattern.CASE_INSENSITIVE);

    private final Messenger messenger;
    private final SauceNaoService sauceNaoService;
    private final TaskExecutor botBackgroundExecutor;

    public SearchImageCommandHandler(Messenger messenger,
                                    SauceNaoService sauceNaoService,
                                    @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor) {
        this.messenger = messenger;
        this.sauceNaoService = sauceNaoService;
        this.botBackgroundExecutor = botBackgroundExecutor;
    }

    @Override
    public void execute(BotContext context) {
        String text = resolveInputText(context);
        if (text.isBlank()) {
            Replier.of(context, messenger).text("请发送：/searchimg 图片链接\n支持 JPG/PNG/GIF/WebP 等常见格式。");
            return;
        }

        String imageUrl = extractFirstImageUrl(text);
        if (imageUrl.isBlank()) {
            Replier.of(context, messenger).text("未找到图片链接，请发送包含图片 URL 的消息。");
            return;
        }

        botBackgroundExecutor.execute(() -> searchAndSend(context, imageUrl));
    }

    private void searchAndSend(BotContext context, String imageUrl) {
        Replier replier = Replier.of(context, messenger);
        Integer progressMsgId = null;
        try {
            progressMsgId = replier.htmlAndReturnId("<b>🔍 搜图中...</b>");

            SearchResponse response = sauceNaoService.searchByUrl(imageUrl);

            if (!response.success() && response.results().isEmpty()) {
                replier.editHtml(progressMsgId, "<b>❌ 未找到结果</b>\n" + BotUtil.escapeHtml(response.message()));
                return;
            }

            // Delete progress message
            replier.deleteSilently(progressMsgId);

            // Send thumbnail first
            List<SearchResult> results = response.results();
            SearchResult top = results.get(0);

            if (top.thumbnailUrl() != null && !top.thumbnailUrl().isBlank()) {
                String caption = buildCaption(top);
                messenger.sendPhotoByUrl(context.chatId(), top.thumbnailUrl(), caption);
            }

            // Send detailed results as text
            String detailText = buildDetailText(response.message(), results);
            replier.html(detailText);

            log.info("以图搜图完成。chatId={}, resultCount={}", context.chatId(), results.size());
        } catch (Exception e) {
            log.warn("以图搜图失败。chatId={}, url={}", context.chatId(), imageUrl, e);
            if (progressMsgId != null) {
                replier.editHtml(progressMsgId, "<b>❌ 搜索失败</b>\n" + BotUtil.escapeHtml(e.getMessage()));
            } else {
                replier.html("<b>❌ 搜索失败</b>\n" + BotUtil.escapeHtml(e.getMessage()));
            }
        }
    }

    private String extractFirstImageUrl(String text) {
        if (text == null || text.isBlank()) return "";
        Matcher matcher = IMAGE_URL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(0) : "";
    }

    private String resolveInputText(BotContext context) {
        if (context == null) return "";
        String direct = context.text() != null ? context.text().strip() : "";
        if (!direct.isBlank()) return direct;
        if (context.message() == null) return "";
        return BotUtil.messageTextOrCaption(context.message().getReplyToMessage());
    }

    private String buildCaption(SearchResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>🔍 以图搜图 - 最佳匹配</b>");
        if (result.title() != null && !result.title().isBlank()) {
            sb.append("\n\n<b>标题：</b>").append(BotUtil.escapeHtml(result.title()));
        }
        if (result.author() != null && !result.author().isBlank()) {
            sb.append("\n<b>作者：</b>").append(BotUtil.escapeHtml(result.author()));
        }
        if (result.platform() != null && !result.platform().isBlank()) {
            sb.append("\n<b>平台：</b>").append(BotUtil.escapeHtml(result.platform()));
        }
        if (result.similarity() != null && !result.similarity().isBlank()) {
            sb.append("\n<b>相似度：</b>").append(result.similarity()).append("%");
        }
        if (result.sourceUrl() != null && !result.sourceUrl().isBlank()) {
            sb.append("\n\n🔗 <a href=\"").append(BotUtil.escapeHtml(result.sourceUrl()))
                    .append("\">查看来源</a>");
        }
        return sb.toString();
    }

    private String buildDetailText(String message, List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>🔍 搜索结果（仅显示相似度 >50%）</b>\n\n");
        sb.append(message).append("\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("<b>▎结果 ").append(i + 1).append("</b>");
            if (r.similarity() != null && !r.similarity().isBlank()) {
                sb.append(" <i>相似度 ").append(r.similarity()).append("%</i>");
            }
            sb.append("\n");
            if (r.platform() != null && !r.platform().isBlank()) {
                sb.append("平台：").append(BotUtil.escapeHtml(r.platform())).append("\n");
            }
            if (r.title() != null && !r.title().isBlank()) {
                sb.append("标题：").append(BotUtil.escapeHtml(r.title())).append("\n");
            }
            if (r.author() != null && !r.author().isBlank()) {
                sb.append("作者：").append(BotUtil.escapeHtml(r.author())).append("\n");
            }
            if (r.sourceUrl() != null && !r.sourceUrl().isBlank()) {
                sb.append("链接：").append(BotUtil.escapeHtml(r.sourceUrl())).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }
}
