package com.github.anicmv.telegrambot.handler.inline.chosen.impl;

import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.handler.inline.chosen.ChosenInlineQueryResultHandler;
import com.github.anicmv.telegrambot.service.DouyinVideoService;
import com.github.anicmv.telegrambot.service.DouyinVideoService.ResolvedVideo;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.utils.BotUtil;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/5/3 18:02
 * @description 抖音已选内联结果处理器，异步替换占位消息为解析结果。
 */
@BotInline(BotConstant.INLINE_ID_DOUYIN)
@Log4j2
@Component
public class DouyinChosenInlineQueryHandler implements ChosenInlineQueryResultHandler {

    private final Messenger messenger;
    private final DouyinVideoService douyinVideoService;
    private final TaskExecutor botBackgroundExecutor;
    private final boolean uploadVideoEnabled;
    private static final String INLINE_LOADING_BUTTON_TEXT = "抖音";
    private static final String INLINE_IMAGE_NOT_SUPPORTED_TEXT = "<b>▎解 析 失 败</b>\n当前链接可能是图片/图集，暂不支持图片解析。";

    public DouyinChosenInlineQueryHandler(Messenger messenger,
                                          DouyinVideoService douyinVideoService,
                                          @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor,
                                          @Value("${bot.douyin.upload-video-enabled:true}") boolean uploadVideoEnabled) {
        this.messenger = messenger;
        this.douyinVideoService = douyinVideoService;
        this.botBackgroundExecutor = botBackgroundExecutor;
        this.uploadVideoEnabled = uploadVideoEnabled;
    }

    @Override
    public void execute(BotContext context) {
        if (context.chosenInlineQuery().getInlineMessageId() == null
                || context.chosenInlineQuery().getInlineMessageId().isBlank()) {
            return;
        }
        botBackgroundExecutor.execute(() -> process(context));
    }

    private void process(BotContext context) {
        String inlineMessageId = context.chosenInlineQuery().getInlineMessageId();
        String prompt = extractPrompt(context.text());
        try {
            boolean parsingEdited = messenger.editInlineMessageTextWithNoopButton(
                    inlineMessageId,
                    "<b>▎解 析 中...</b>",
                    "HTML",
                    INLINE_LOADING_BUTTON_TEXT
            );
            if (!parsingEdited) {
                log.warn("Failed to update inline status to parsing. inlineMessageId={}", inlineMessageId);
            }
            ResolvedVideo video = douyinVideoService.resolve(prompt);
            if (uploadVideoEnabled) {
                boolean uploadingEdited = messenger.editInlineMessageTextWithNoopButton(
                        inlineMessageId,
                        "<b>▎上 传 中...</b>",
                        "HTML",
                        INLINE_LOADING_BUTTON_TEXT
                );
                if (!uploadingEdited) {
                    log.warn("Failed to update inline status to uploading. inlineMessageId={}", inlineMessageId);
                }
                boolean edited = messenger.editInlineMessageVideo(
                        inlineMessageId,
                        video.downloadUrl(),
                        buildRichText(video),
                        "HTML"
                );
                if (!edited) {
                    messenger.editInlineMessageText(
                            inlineMessageId,
                            INLINE_IMAGE_NOT_SUPPORTED_TEXT,
                            "HTML",
                            true
                    );
                }
            } else {
                boolean edited = messenger.editInlineMessageTextWithNoopButton(
                        inlineMessageId,
                        buildRichText(video),
                        "HTML",
                        INLINE_LOADING_BUTTON_TEXT
                );
                if (!edited) {
                    messenger.editInlineMessageText(
                            inlineMessageId,
                            INLINE_IMAGE_NOT_SUPPORTED_TEXT,
                            "HTML",
                            true
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Douyin chosen inline processing failed. inlineMessageId={}", inlineMessageId, e);
            messenger.editInlineMessageText(
                    inlineMessageId,
                    INLINE_IMAGE_NOT_SUPPORTED_TEXT,
                    "HTML",
                    true
            );
        }
    }

    private String buildRichText(ResolvedVideo video) {
        String title = video.desc() == null || video.desc().isBlank() ? ("抖音视频 " + video.id()) : video.desc();
        StringBuilder caption = new StringBuilder();
        caption.append("<b>").append(BotUtil.escapeHtml(title)).append("</b>");
        if (video.author() != null && !video.author().isBlank()) {
            caption.append("\n\n<blockquote>作者：")
                    .append(BotUtil.escapeHtml(video.author()))
                    .append("</blockquote>");
        }
        boolean hasSource = video.sourceUrl() != null && !video.sourceUrl().isBlank();
        boolean hasResolved = video.realVideoUrl() != null && !video.realVideoUrl().isBlank();
        if (hasSource || hasResolved) {
            caption.append("\n\n▎");
            if (hasSource) {
                caption.append("<a href=\"").append(BotUtil.escapeHtml(video.sourceUrl())).append("\">视频链接</a>");
            }
            if (hasSource && hasResolved) {
                caption.append("  |  ");
            }
            if (hasResolved) {
                caption.append("<a href=\"").append(BotUtil.escapeHtml(video.realVideoUrl())).append("\">立即观看</a>");
            }
        }
        String value = caption.toString();
        return value.length() > 3500 ? value.substring(0, 3497) + "..." : value;
    }

    private String extractPrompt(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String trimmed = query.trim();
        int firstBlank = trimmed.indexOf(' ');
        if (firstBlank < 0) {
            return "";
        }
        return trimmed.substring(firstBlank + 1).trim();
    }
}
