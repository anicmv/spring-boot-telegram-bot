package com.github.anicmv.telegrambot.handler.inline.provider.impl;

import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProvider;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.model.BotContext;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description XP inline 结果提供器。
 */
@BotInline(BotConstant.INLINE_ID_XP)
@Component
public class XpInlineQueryResultProvider implements InlineQueryResultProvider {

    @Override
    public boolean supports(BotContext context) {
        String query = context == null ? null : context.text();
        if (query == null || query.isBlank()) {
            return true;
        }
        return query.toLowerCase().contains("xp");
    }

    @Override
    public InlineQueryResult createResult(BotContext context) {
        String imageUrl = "https://jpg.moe/i/3eh09458.jpeg";
        List<InlineKeyboardButton> allButtons = List.of(
                InlineKeyboardButton.builder().text("白丝").callbackData(BotConstant.CALLBACK_XP_BS).build(),
                InlineKeyboardButton.builder().text("JK").callbackData(BotConstant.CALLBACK_XP_JK).build(),
                InlineKeyboardButton.builder().text("黑丝").callbackData(BotConstant.CALLBACK_XP_HS).build(),
                InlineKeyboardButton.builder().text("默认").callbackData(BotConstant.CALLBACK_XP_DEFAULT).build()
        );
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        for (int i = 0; i < allButtons.size(); i += 3) {
            InlineKeyboardRow row = new InlineKeyboardRow();
            for (int j = i; j < Math.min(i + 3, allButtons.size()); j++) {
                row.add(allButtons.get(j));
            }
            keyboard.add(row);
        }
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder().keyboard(keyboard).build();
        return InlineQueryResultPhoto.builder()
                .id(sortId())
                .photoUrl(imageUrl)
                .thumbnailUrl(imageUrl)
                .title("XP")
                .replyMarkup(markup)
                .build();
    }
}
