package com.github.anicmv.telegrambot.handler.inline.provider.impl;

import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProvider;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.model.BotContext;
import java.util.List;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

/**
 * @author anicmv
 * @date 2026/3/21
 * @description 红娘系统 inline 结果提供器。
 */
@BotInline(BotConstant.INLINE_ID_MATCHMAKER)
@Component
public class MatchmakerInlineQueryResultProvider implements InlineQueryResultProvider {

    private static final String PLACEHOLDER_IMAGE_URL = "https://jpg.moe/i/rp8dpcn2.jpeg";

    @Override
    public boolean supports(BotContext context) {
        String query = context == null ? null : context.text();
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalized = query.toLowerCase();
        return normalized.contains("matchmaker")
                || normalized.contains("wife")
                || normalized.contains("husband")
                || query.contains("红娘")
                || query.contains("老婆")
                || query.contains("老公");
    }

    @Override
    public InlineQueryResult createResult(BotContext context) {
        String fullName = context.inlineQuery().getFrom().getFirstName()
                + (context.inlineQuery().getFrom().getLastName() == null ? "" : context.inlineQuery().getFrom().getLastName());
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("\uD83D\uDC95" + fullName + "\uD83D\uDC95")
                .callbackData(BotConstant.CALLBACK_ACTION_PING + ":matchmaker")
                .build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button)));
        return InlineQueryResultPhoto.builder()
                .id(sortId())
                .photoUrl(PLACEHOLDER_IMAGE_URL)
                .thumbnailUrl(PLACEHOLDER_IMAGE_URL)
                .title("红娘系统")
                .caption("红娘系统启动中，正在给你摇一位缘分...")
                .replyMarkup(markup)
                .build();
    }
}
