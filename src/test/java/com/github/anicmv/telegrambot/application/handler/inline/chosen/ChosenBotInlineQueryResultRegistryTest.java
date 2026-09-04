package com.github.anicmv.telegrambot.application.handler.inline.chosen;

import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.handler.inline.chosen.ChosenInlineQueryResultHandler;
import com.github.anicmv.telegrambot.handler.inline.chosen.ChosenInlineQueryResultRegistry;
import com.github.anicmv.telegrambot.model.BotContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChosenBotInlineQueryResultRegistryTest {

    @Test
    void shouldFindByResultId() {
        DouyinTestHandler douyin = new DouyinTestHandler();
        ChosenInlineQueryResultRegistry registry = new ChosenInlineQueryResultRegistry(List.of(douyin));

        assertSame(douyin, registry.find("DY"));
    }

    @Test
    void shouldReturnNullForUnknownResultId() {
        ChosenInlineQueryResultRegistry registry = new ChosenInlineQueryResultRegistry(List.of(new DouyinTestHandler()));

        assertNull(registry.find("UNKNOWN"));
    }

    @Test
    void shouldReturnNullForNullResultId() {
        ChosenInlineQueryResultRegistry registry = new ChosenInlineQueryResultRegistry(List.of(new DouyinTestHandler()));

        assertNull(registry.find(null));
    }

    @Test
    void shouldFailFastWhenResultIdIsDuplicated() {
        assertThrows(IllegalStateException.class, () -> new ChosenInlineQueryResultRegistry(List.of(
                new DouyinTestHandler(),
                new DuplicateDouyinTestHandler()
        )));
    }

    @Test
    void shouldFailFastWhenInlineResultIdAnnotationIsMissing() {
        assertThrows(IllegalStateException.class, () -> new ChosenInlineQueryResultRegistry(List.of(
                new UnannotatedTestHandler()
        )));
    }

    @BotInline("DY")
    private static class DouyinTestHandler implements ChosenInlineQueryResultHandler {
        @Override
        public void execute(BotContext context) {
        }
    }

    @BotInline("DY")
    private static class DuplicateDouyinTestHandler implements ChosenInlineQueryResultHandler {
        @Override
        public void execute(BotContext context) {
        }
    }

    private static class UnannotatedTestHandler implements ChosenInlineQueryResultHandler {
        @Override
        public void execute(BotContext context) {
        }
    }
}
