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
 * @date 2026/3/15 21:27
 * @description 随机二次元图片 inline 结果提供器。
 */
@BotInline(BotConstant.INLINE_ID_RANDOM_ECY)
@Component
public class RandomEcyImageInlineQueryResultProvider implements InlineQueryResultProvider {

    @Override
    public boolean supports(BotContext context) {
        String query = context == null ? null : context.text();
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalized = query.toLowerCase();
        return normalized.contains("ecy")
                || normalized.contains("random")
                || query.contains("二次元");
    }

    @Override
    public InlineQueryResult createResult(BotContext context) {
        String imageUrl = "https://jpg.moe/i/rp8dpcn2.jpeg";
        String fullName = context.inlineQuery().getFrom().getFirstName()
                + (context.inlineQuery().getFrom().getLastName() == null ? "" : context.inlineQuery().getFrom().getLastName());
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("\uD83D\uDE0D" + fullName + "\uD83D\uDE0D")
                .callbackData(BotConstant.CALLBACK_ACTION_PING + ":ecy")
                .build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button)));
        return InlineQueryResultPhoto.builder()
                .id(sortId())
                .photoUrl(imageUrl)
                .thumbnailUrl(imageUrl)
                .title("随机二次元")
                .replyMarkup(markup)
                .build();
    }
}
