package com.github.anicmv.telegrambot.application.handler.callback;

import com.github.anicmv.telegrambot.annotation.BotCallback;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.handler.callback.CallbackActionHandler;
import com.github.anicmv.telegrambot.handler.callback.CallbackActionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BotCallbackRegistryTest {

    @Test
    void shouldFindDirectAction() {
        PingTestHandler ping = new PingTestHandler();
        CallbackActionRegistry registry = new CallbackActionRegistry(List.of(ping));

        assertSame(ping, registry.find("PING"));
    }

    @Test
    void shouldFindWildcardAction() {
        XpTestHandler xp = new XpTestHandler();
        CallbackActionRegistry registry = new CallbackActionRegistry(List.of(xp));

        assertSame(xp, registry.find("XP_HS"));
    }

    @Test
    void shouldReturnNullForUnknownAction() {
        CallbackActionRegistry registry = new CallbackActionRegistry(List.of(new PingTestHandler()));

        assertNull(registry.find("UNKNOWN"));
    }

    @Test
    void shouldFailFastWhenActionIsDuplicated() {
        assertThrows(IllegalStateException.class, () -> new CallbackActionRegistry(List.of(
                new PingTestHandler(),
                new DuplicatePingTestHandler()
        )));
    }

    @Test
    void shouldFailFastWhenCallbackActionAnnotationIsMissing() {
        assertThrows(IllegalStateException.class, () -> new CallbackActionRegistry(List.of(
                new UnannotatedTestHandler()
        )));
    }

    @BotCallback("PING")
    private static class PingTestHandler implements CallbackActionHandler {
        @Override
        public void execute(BotContext context, String payload) {
        }
    }

    @BotCallback("PING")
    private static class DuplicatePingTestHandler implements CallbackActionHandler {
        @Override
        public void execute(BotContext context, String payload) {
        }
    }

    @BotCallback("XP_*")
    private static class XpTestHandler implements CallbackActionHandler {
        @Override
        public void execute(BotContext context, String payload) {
        }
    }

    private static class UnannotatedTestHandler implements CallbackActionHandler {
        @Override
        public void execute(BotContext context, String payload) {
        }
    }
}
