package com.github.anicmv.telegrambot.application.handler.inline;

import com.github.anicmv.telegrambot.handler.HandlerResult;
import com.github.anicmv.telegrambot.handler.inline.InlineQueryUpdateHandler;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProviderRegistry;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotInlineQueryUpdateHandlerTest {

    @Test
    void shouldPaginateNormalInlineResults() {
        Messenger messenger = mock(Messenger.class);
        InlineQueryResultProviderRegistry registry = mock(InlineQueryResultProviderRegistry.class);
        InlineQueryUpdateHandler handler = new InlineQueryUpdateHandler(messenger, registry);
        InlineQuery inlineQuery = mock(InlineQuery.class);
        List<InlineQueryResult> results = buildResults(15);
        when(inlineQuery.getId()).thenReturn("inline-1");
        when(inlineQuery.getQuery()).thenReturn("hello");
        when(inlineQuery.getOffset()).thenReturn("1");
        when(registry.createAll(any())).thenReturn(results);
        BotContext context = new BotContext(null, UpdateType.INLINE_QUERY, null, 1L, "hello", null, null, inlineQuery, null);

        HandlerResult result = handler.handle(context);

        assertSame(HandlerResult.STOP, result);
        verify(messenger).answerInline(eq("inline-1"), eq(results.subList(12, 15)), eq(""));
    }

    @Test
    void shouldReturnAllResultsInDbMode() {
        Messenger messenger = mock(Messenger.class);
        InlineQueryResultProviderRegistry registry = mock(InlineQueryResultProviderRegistry.class);
        InlineQueryUpdateHandler handler = new InlineQueryUpdateHandler(messenger, registry);
        InlineQuery inlineQuery = mock(InlineQuery.class);
        List<InlineQueryResult> results = buildResults(2);
        when(inlineQuery.getId()).thenReturn("inline-2");
        when(inlineQuery.getQuery()).thenReturn("db movie");
        when(inlineQuery.getOffset()).thenReturn("");
        when(registry.createAll(any())).thenReturn(results);
        BotContext context = new BotContext(null, UpdateType.INLINE_QUERY, null, 1L, "db movie", null, null, inlineQuery, null);

        HandlerResult result = handler.handle(context);

        assertSame(HandlerResult.STOP, result);
        verify(messenger).answerInline(eq("inline-2"), eq(results), eq(""));
    }

    @Test
    void shouldNotKeepPaginatingDbModeWhenOffsetExists() {
        Messenger messenger = mock(Messenger.class);
        InlineQueryResultProviderRegistry registry = mock(InlineQueryResultProviderRegistry.class);
        InlineQueryUpdateHandler handler = new InlineQueryUpdateHandler(messenger, registry);
        InlineQuery inlineQuery = mock(InlineQuery.class);
        List<InlineQueryResult> results = buildResults(2);
        when(inlineQuery.getId()).thenReturn("inline-3");
        when(inlineQuery.getQuery()).thenReturn("db movie");
        when(inlineQuery.getOffset()).thenReturn("3");
        when(registry.createAll(any())).thenReturn(results);
        BotContext context = new BotContext(null, UpdateType.INLINE_QUERY, null, 1L, "db movie", null, null, inlineQuery, null);

        HandlerResult result = handler.handle(context);

        assertSame(HandlerResult.STOP, result);
        verify(messenger).answerInline(eq("inline-3"), eq(results), eq(""));
    }

    private List<InlineQueryResult> buildResults(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> InlineQueryResultArticle.builder()
                        .id("id-" + index)
                        .title("title-" + index)
                        .inputMessageContent(InputTextMessageContent.builder().messageText("text-" + index).build())
                        .build())
                .map(InlineQueryResult.class::cast)
                .toList();
    }
}
