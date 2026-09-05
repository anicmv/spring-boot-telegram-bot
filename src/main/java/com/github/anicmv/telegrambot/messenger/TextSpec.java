package com.github.anicmv.telegrambot.messenger;

import com.github.anicmv.telegrambot.model.InlineButton;

import java.util.List;

/**
 * @author anicmv
 * @date 2026/9/5
 * @description 文本消息发送参数对象：折叠 parseMode / 回复 / 按钮 三个维度的组合，
 * 供 {@link Messenger#sendTextMessage(TextSpec)} 统一发送。
 */
public record TextSpec(
        Long chatId,
        String text,
        String parseMode,
        Integer replyToMessageId,
        List<InlineButton> callbackButtons,
        String switchButtonText,
        String switchInlineQuery
) {

    public static final String PARSE_HTML = "HTML";
    public static final String PARSE_MARKDOWN_V2 = "MarkdownV2";

    public static TextSpec of(Long chatId, String text) {
        return new TextSpec(chatId, text, null, null, null, null, null);
    }

    public TextSpec parseMode(String parseMode) {
        return new TextSpec(chatId, text, parseMode, replyToMessageId, callbackButtons, switchButtonText, switchInlineQuery);
    }

    public TextSpec html() {
        return parseMode(PARSE_HTML);
    }

    public TextSpec markdownV2() {
        return parseMode(PARSE_MARKDOWN_V2);
    }

    public TextSpec replyTo(Integer replyToMessageId) {
        return new TextSpec(chatId, text, parseMode, replyToMessageId, callbackButtons, switchButtonText, switchInlineQuery);
    }

    public TextSpec callbackButtons(List<InlineButton> callbackButtons) {
        return new TextSpec(chatId, text, parseMode, replyToMessageId, callbackButtons, switchButtonText, switchInlineQuery);
    }

    public TextSpec switchInline(String buttonText, String inlineQuery) {
        return new TextSpec(chatId, text, parseMode, replyToMessageId, callbackButtons, buttonText,
                inlineQuery == null ? "" : inlineQuery);
    }

    public boolean hasCallbackButtons() {
        return callbackButtons != null && !callbackButtons.isEmpty();
    }

    public boolean hasSwitchInline() {
        return switchInlineQuery != null;
    }

    /**
     * 观测事件类型，与原 TelegramMessenger 各方法的字面值保持一致。
     */
    public String eventKind() {
        String base = switch (parseMode == null ? "" : parseMode) {
            case PARSE_HTML -> "html_message";
            case PARSE_MARKDOWN_V2 -> "markdown_v2_message";
            default -> "message";
        };
        if (hasCallbackButtons()) {
            base = base + "_with_inline_buttons";
        }
        if (hasSwitchInline()) {
            base = base + "_with_switch_inline_button";
        }
        return replyToMessageId != null ? "reply_" + base : base;
    }
}
