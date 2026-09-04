package com.github.anicmv.telegrambot.handler.inline.provider.impl;

import com.github.anicmv.telegrambot.service.AiAccessControlService;
import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProvider;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.model.BotContext;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.List;

/**
 * @author anicmv
 * @date 2026/5/1 15:18
 * @description DeepSeek inline 结果提供器。
 */
@BotInline(BotConstant.INLINE_ID_DEEPSEEK)
@Component
public class DeepSeekInlineQueryResultProvider implements InlineQueryResultProvider {

    private final AiAccessControlService aiAccessControlService;

    public DeepSeekInlineQueryResultProvider(AiAccessControlService aiAccessControlService) {
        this.aiAccessControlService = aiAccessControlService;
    }

    @Override
    public boolean supports(BotContext context) {
        if (context == null || aiAccessControlService.isBlocked(context.userId())) {
            return false;
        }
        String query = context == null ? null : context.text();
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = query.trim().toLowerCase();
        if (normalized.startsWith("ai ")) {
            return !extractPrompt(query).isBlank();
        }
        if (normalized.startsWith("ds ")) {
            return !extractPrompt(query).isBlank();
        }
        return false;
    }

    @Override
    public InlineQueryResult createResult(BotContext context) {
        String prompt = extractPrompt(context == null ? null : context.text());
        return InlineQueryResultArticle.builder()
                .id(sortId())
                .title("DeepSeek")
                .description(limit(prompt.isBlank() ? "发送后异步生成回答" : prompt, 80))
                .inputMessageContent(InputTextMessageContent.builder().messageText(prompt).build())
                .replyMarkup(loadingMarkup())
                .build();
    }

    private InlineKeyboardMarkup loadingMarkup() {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("DeepSeek 正在处理...")
                .callbackData(BotConstant.CALLBACK_ACTION_NOOP)
                .build();
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button)));
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

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
