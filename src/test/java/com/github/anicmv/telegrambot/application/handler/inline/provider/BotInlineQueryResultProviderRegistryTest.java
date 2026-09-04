package com.github.anicmv.telegrambot.application.handler.inline.provider;

import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProvider;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProviderRegistry;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BotInlineQueryResultProviderRegistryTest {

    @Test
    void shouldSortProvidersBySortId() {
        InlineQueryResultProviderRegistry registry = new InlineQueryResultProviderRegistry(List.of(
                new ProviderB(),
                new ProviderA()
        ));

        assertEquals(List.of("A", "B"), registry.providers().stream().map(InlineQueryResultProvider::sortId).toList());
    }

    @Test
    void shouldOnlyReturnDbProviderWhenQueryStartsWithDb() {
        InlineQueryResultProviderRegistry registry = new InlineQueryResultProviderRegistry(List.of(
                new ProviderA(),
                new DbProvider()
        ));
        BotContext context = new BotContext(null, UpdateType.INLINE_QUERY, null, 1L, "db movie", null, null, null, null);

        List<String> ids = registry.createAll(context).stream()
                .map(InlineQueryResultArticle.class::cast)
                .map(InlineQueryResultArticle::getId)
                .toList();

        assertEquals(List.of(BotConstant.INLINE_ID_DB), ids);
    }

    private static InlineQueryResult articleOfSortId(InlineQueryResultProvider provider) {
        return InlineQueryResultArticle.builder()
                .id(provider.sortId())
                .title(provider.sortId())
                .inputMessageContent(InputTextMessageContent.builder().messageText(provider.sortId()).build())
                .build();
    }

    @BotInline("A")
    private static class ProviderA implements InlineQueryResultProvider {
        @Override
        public boolean supports(BotContext context) {
            return true;
        }

        @Override
        public InlineQueryResult createResult(BotContext context) {
            return articleOfSortId(this);
        }
    }

    @BotInline("B")
    private static class ProviderB implements InlineQueryResultProvider {
        @Override
        public boolean supports(BotContext context) {
            return true;
        }

        @Override
        public InlineQueryResult createResult(BotContext context) {
            return articleOfSortId(this);
        }
    }

    @BotInline(BotConstant.INLINE_ID_DB)
    private static class DbProvider implements InlineQueryResultProvider {
        @Override
        public boolean supports(BotContext context) {
            return true;
        }

        @Override
        public InlineQueryResult createResult(BotContext context) {
            return articleOfSortId(this);
        }
    }
}
