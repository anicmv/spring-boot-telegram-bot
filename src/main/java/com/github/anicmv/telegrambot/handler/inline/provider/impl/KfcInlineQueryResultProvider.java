package com.github.anicmv.telegrambot.handler.inline.provider.impl;

import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProvider;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.utils.ShadiaoCopywritingUtil;
import com.github.anicmv.telegrambot.model.BotContext;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultPhoto;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description KFC 文案 inline 结果提供器。
 */
@BotInline(BotConstant.INLINE_ID_KFC)
@Component
public class KfcInlineQueryResultProvider implements InlineQueryResultProvider {

    @Override
    public boolean supports(BotContext context) {
        String query = context == null ? null : context.text();
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = query.toLowerCase();
        return normalized.contains("kfc") || query.contains("肯德基");
    }

    @Override
    public InlineQueryResult createResult(BotContext context) {
        String text = ShadiaoCopywritingUtil.fetchText(BotConstant.API_KFC, "今天没拿到 KFC 文案。");
        InputTextMessageContent content = InputTextMessageContent.builder().messageText(text).build();
        return InlineQueryResultPhoto.builder()
                .id(sortId())
                .photoUrl("https://jpg.moe/i/zwbsy5a9.png")
                .thumbnailUrl("https://jpg.moe/i/zwbsy5a9.png")
                .title("kfc文案")
                .inputMessageContent(content)
                .build();
    }
}
