package com.github.anicmv.telegrambot.application.handler.command;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.handler.command.BotCommandRegistry;
import com.github.anicmv.telegrambot.model.BotContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BotCommandRegistryTest {

    @Test
    void shouldFindDirectCommand() {
        PingTestHandler ping = new PingTestHandler();
        BotCommandRegistry registry = new BotCommandRegistry(List.of(ping));

        assertSame(ping, registry.find("/ping"));
    }

    @Test
    void shouldFindMentionedCommand() {
        PingTestHandler ping = new PingTestHandler();
        BotCommandRegistry registry = new BotCommandRegistry(List.of(ping));

        assertSame(ping, registry.find("/ping@test_bot"));
    }

    @Test
    void shouldReturnNullForUnknownCommand() {
        BotCommandRegistry registry = new BotCommandRegistry(List.of(new PingTestHandler()));

        assertNull(registry.find("/help"));
    }

    @Test
    void shouldFailFastWhenCommandIsDuplicated() {
        assertThrows(IllegalStateException.class, () -> new BotCommandRegistry(List.of(
                new PingTestHandler(),
                new DuplicatePingTestHandler()
        )));
    }

    @Test
    void shouldFailFastWhenBotCommandAnnotationIsMissing() {
        assertThrows(IllegalStateException.class, () -> new BotCommandRegistry(List.of(
                new UnannotatedTestHandler()
        )));
    }

    @Test
    void shouldReturnDescribedCommandsSortedAndSkipBlankDescription() {
        BotCommandRegistry registry = new BotCommandRegistry(List.of(
                new StartTestHandler(),
                new AiTestHandler(),
                new PingTestHandler()
        ));

        assertEquals(
                List.of(
                        new BotCommandRegistry.CommandInfo("/ai", "调用 DeepSeek"),
                        new BotCommandRegistry.CommandInfo("/start", "初始化并展示用户信息")
                ),
                registry.describedCommands()
        );
    }

    @BotCommand("/ping")
    private static class PingTestHandler implements BotCommandHandler {
        @Override
        public void execute(BotContext context) {
        }
    }

    @BotCommand("/ping")
    private static class DuplicatePingTestHandler implements BotCommandHandler {
        @Override
        public void execute(BotContext context) {
        }
    }

    private static class UnannotatedTestHandler implements BotCommandHandler {
        @Override
        public void execute(BotContext context) {
        }
    }

    @BotCommand(value = "/start", description = "初始化并展示用户信息")
    private static class StartTestHandler implements BotCommandHandler {
        @Override
        public void execute(BotContext context) {
        }
    }

    @BotCommand(value = "/ai", description = "调用 DeepSeek")
    private static class AiTestHandler implements BotCommandHandler {
        @Override
        public void execute(BotContext context) {
        }
    }
}
