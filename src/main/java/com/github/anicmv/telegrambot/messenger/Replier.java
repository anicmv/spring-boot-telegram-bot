package com.github.anicmv.telegrambot.messenger;

import com.github.anicmv.telegrambot.model.BotContext;

/**
 * @author anicmv
 * @date 2026/9/5
 * @description 当前上下文的回复策略：更新来自普通消息时以回复语义发送，否则直发。
 * 收敛各命令 handler 中复制的 replyText / replyHtml / 进度占位消息 变体。
 */
public record Replier(BotContext context, Messenger messenger) {

    public static Replier of(BotContext context, Messenger messenger) {
        return new Replier(context, messenger);
    }

    public void text(String text) {
        Integer replyTo = replyToMessageId();
        if (replyTo != null) {
            messenger.sendReplyText(chatId(), replyTo, text);
        } else {
            messenger.sendText(chatId(), text);
        }
    }

    /**
     * 回复场景返回新消息 messageId（供后续编辑/删除）；非消息场景直发并返回 null。
     */
    public Integer textAndReturnId(String text) {
        Integer replyTo = replyToMessageId();
        if (replyTo != null) {
            return messenger.sendReplyTextAndReturnMessageId(chatId(), replyTo, text);
        }
        messenger.sendText(chatId(), text);
        return null;
    }

    public void html(String html) {
        Integer replyTo = replyToMessageId();
        if (replyTo != null) {
            messenger.sendReplyHtmlText(chatId(), replyTo, html);
        } else {
            messenger.sendHtmlText(chatId(), html);
        }
    }

    /**
     * 发送 HTML 占位/进度消息并返回 messageId，失败返回 null。
     */
    public Integer htmlAndReturnId(String html) {
        Integer replyTo = replyToMessageId();
        return replyTo != null
                ? messenger.sendReplyHtmlTextAndReturnMessageId(chatId(), replyTo, html)
                : messenger.sendHtmlTextAndReturnMessageId(chatId(), html);
    }

    public void markdownV2(String text) {
        Integer replyTo = replyToMessageId();
        if (replyTo != null) {
            messenger.sendReplyMarkdownV2Text(chatId(), replyTo, text);
        } else {
            messenger.sendMarkdownV2Text(chatId(), text);
        }
    }

    /**
     * MarkdownV2 回复场景返回新消息 messageId；非消息场景直发并返回 null。
     */
    public Integer markdownV2AndReturnId(String text) {
        Integer replyTo = replyToMessageId();
        if (replyTo != null) {
            return messenger.sendReplyMarkdownV2TextAndReturnMessageId(chatId(), replyTo, text);
        }
        messenger.sendMarkdownV2Text(chatId(), text);
        return null;
    }

    /**
     * 以 HTML 模式编辑占位消息；messageId 为 null 时忽略。
     */
    public void editHtml(Integer messageId, String html) {
        if (messageId != null) {
            messenger.editMessageText(chatId(), messageId, html, TextSpec.PARSE_HTML);
        }
    }

    /**
     * 静默删除消息；messageId 为 null 时忽略。
     */
    public void deleteSilently(Integer messageId) {
        if (messageId != null) {
            messenger.deleteMessageSilently(chatId(), messageId);
        }
    }

    public void switchInlineButton(String text, String buttonText, String inlineQuery) {
        Integer replyTo = replyToMessageId();
        if (replyTo != null) {
            messenger.sendReplyTextWithSwitchInlineButton(chatId(), replyTo, text, buttonText, inlineQuery);
        } else {
            messenger.sendTextWithSwitchInlineButton(chatId(), text, buttonText, inlineQuery);
        }
    }

    public boolean videoByPath(String videoPath, String caption) {
        Integer replyTo = replyToMessageId();
        return replyTo != null
                ? messenger.sendReplyVideoByPath(chatId(), replyTo, videoPath, caption)
                : messenger.sendVideoByPath(chatId(), videoPath, caption);
    }

    public boolean documentByPath(String documentPath, String caption) {
        Integer replyTo = replyToMessageId();
        return replyTo != null
                ? messenger.sendReplyDocumentByPath(chatId(), replyTo, documentPath, caption)
                : messenger.sendDocumentByPath(chatId(), documentPath, caption);
    }

    private Integer replyToMessageId() {
        if (context == null || context.message() == null) {
            return null;
        }
        return context.message().getMessageId();
    }

    private Long chatId() {
        return context == null ? null : context.chatId();
    }
}
