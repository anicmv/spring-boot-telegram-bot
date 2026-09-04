package com.github.anicmv.telegrambot.handler.inline.provider.impl;

import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProvider;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.model.BotContext;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description Inline 文章示例提供器。
 */
@BotInline(BotConstant.INLINE_ID_ARTICLE)
@Component
public class ArticleInlineQueryResultProvider implements InlineQueryResultProvider {

    @Override
    public boolean supports(BotContext context) {
        String query = context == null ? null : context.text();
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalized = query.toLowerCase();
        return normalized.contains("help")
                || normalized.contains("how")
                || query.contains("食用")
                || query.contains("帮助");
    }

    @Override
    public InlineQueryResult createResult(BotContext context) {
        InputTextMessageContent content = InputTextMessageContent.builder()
                .messageText("这里什么都没有...")
                .build();
        return InlineQueryResultArticle.builder()
                .id(sortId())
                .title("\uD83C\uDF5A 如何食用")
                .description("如何食用")
                .inputMessageContent(content)
                .build();
    }
}
