package com.github.anicmv.telegrambot.application.handler.inline.provider.impl;

import com.github.anicmv.telegrambot.service.AiAccessControlService;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.handler.inline.provider.impl.DeepSeekInlineQueryResultProvider;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekBotInlineQueryResultProviderTest {

    private static AiAccessControlService accessControlService(Long... blockedUserIds) {
        BotProperties properties = new BotProperties();
        for (Long blockedUserId : blockedUserIds) {
            properties.getAi().getBlacklistUserIds().add(blockedUserId);
        }
        return new AiAccessControlService(properties);
    }

    @Test
    void shouldSupportAiAndDsPrefix() {
        DeepSeekInlineQueryResultProvider provider = new DeepSeekInlineQueryResultProvider(accessControlService());

        assertTrue(provider.supports(new BotContext(null, UpdateType.INLINE_QUERY, null, 1L, "ai 你好", null, null, null, null)));
        assertTrue(provider.supports(new BotContext(null, UpdateType.INLINE_QUERY, null, 1L, "ds 你好", null, null, null, null)));
        assertFalse(provider.supports(new BotContext(null, UpdateType.INLINE_QUERY, null, 1L, "ai ", null, null, null, null)));
        assertFalse(provider.supports(new BotContext(null, UpdateType.INLINE_QUERY, null, 1L, "ds ", null, null, null, null)));
        assertFalse(provider.supports(new BotContext(null, UpdateType.INLINE_QUERY, null, 1L, "kfc", null, null, null, null)));
    }

    @Test
    void shouldBuildPlaceholderInlineArticle() {
        DeepSeekInlineQueryResultProvider provider = new DeepSeekInlineQueryResultProvider(accessControlService());

        var result = provider.createResult(new BotContext(null, UpdateType.INLINE_QUERY, null, 1L, "ai 你好", null, null, null, null));

        assertInstanceOf(InlineQueryResultArticle.class, result);
        InlineQueryResultArticle article = (InlineQueryResultArticle) result;
        assertNotNull(article.getInputMessageContent());
        assertInstanceOf(InputTextMessageContent.class, article.getInputMessageContent());
        assertNotNull(article.getReplyMarkup());
    }

    @Test
    void shouldNotSupportBlockedUser() {
        DeepSeekInlineQueryResultProvider provider = new DeepSeekInlineQueryResultProvider(accessControlService(2L));

        assertFalse(provider.supports(new BotContext(null, UpdateType.INLINE_QUERY, null, 2L, "ai 你好", null, null, null, null)));
    }
}
