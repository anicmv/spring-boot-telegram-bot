package com.github.anicmv.telegrambot.handler.inline.provider.impl;

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
 * @date 2026/3/15 21:27
 * @description 哔哩哔哩每日放送 inline 结果提供器。
 */
@BotInline(BotConstant.INLINE_ID_BILI)
@Component
public class BiliTimelineInlineQueryResultProvider implements InlineQueryResultProvider {

    @Override
    public boolean supports(BotContext context) {
        String query = context == null ? null : context.text();
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalized = query.toLowerCase();
        return normalized.contains("bili")
                || normalized.contains("timeline")
                || query.contains("哔哩")
                || query.contains("放送");
    }

    @Override
    public InlineQueryResult createResult(BotContext context) {
        String imageUrl = "https://jpg.moe/i/hr58gxep.jpeg";
        InlineKeyboardButton gm = InlineKeyboardButton.builder().text("国漫").callbackData(BotConstant.CALLBACK_BILI_GM).build();
        InlineKeyboardButton rm = InlineKeyboardButton.builder().text("日漫").callbackData(BotConstant.CALLBACK_BILI_RM).build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(gm, rm)));
        InputTextMessageContent content = InputTextMessageContent.builder()
                .messageText("哔哩哔哩每日放送")
                .build();
        return InlineQueryResultArticle.builder()
                .id(sortId())
                .thumbnailUrl(imageUrl)
                .title("哔哩哔哩每日放送")
                .inputMessageContent(content)
                .replyMarkup(markup)
                .build();
    }
}
