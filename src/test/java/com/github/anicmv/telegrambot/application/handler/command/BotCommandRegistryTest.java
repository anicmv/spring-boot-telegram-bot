package com.github.anicmv.telegrambot.application.handler.command;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.handler.command.BotCommandRegistry;
import com.github.anicmv.telegrambot.model.BotContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BotCommandRegistryTest {

    private static final String BOT_USERNAME = "test_bot";

    private static BotCommandRegistry registry(BotCommandHandler... handlers) {
        return new BotCommandRegistry(List.of(handlers), BOT_USERNAME);
    }

    @Test
    void shouldFindDirectCommand() {
        PingTestHandler ping = new PingTestHandler();
        BotCommandRegistry registry = registry(ping);

        assertSame(ping, registry.find("/ping"));
    }

    @Test
    void shouldFindMentionedCommand() {
        PingTestHandler ping = new PingTestHandler();
        BotCommandRegistry registry = registry(ping);

        assertSame(ping, registry.find("/ping@test_bot"));
    }

    @Test
    void shouldReturnNullForUnknownCommand() {
        BotCommandRegistry registry = registry(new PingTestHandler());

        assertNull(registry.find("/help"));
    }

    @Test
    void shouldFindMentionOnlyCommandWithBotUsernameInGroup() {
        AiTestHandler ai = new AiTestHandler();
        BotCommandRegistry registry = registry(ai);

        assertNull(registry.find("/ai", true));
        assertSame(ai, registry.find("/ai@test_bot", true));
        assertSame(ai, registry.find("/ai@TEST_BOT", true));
        assertNull(registry.find("/ai@other_bot", true));
    }

    @Test
    void shouldFindMentionOnlyCommandBareInPrivate() {
        AiTestHandler ai = new AiTestHandler();
        BotCommandRegistry registry = registry(ai);

        assertSame(ai, registry.find("/ai", false));
        assertSame(ai, registry.find("/ai@test_bot", false));
    }

    @Test
    void shouldKeepMentionAgnosticForNormalCommandsInGroup() {
        PingTestHandler ping = new PingTestHandler();
        BotCommandRegistry registry = registry(ping, new AiTestHandler());

        assertSame(ping, registry.find("/ping@other_bot", true));
        assertSame(ping, registry.find("/ping", true));
        assertNull(registry.find("/ai@other_bot", true));
    }

    @Test
    void shouldDegradeToAnyAtSuffixWhenBotUsernameNotConfigured() {
        BotCommandRegistry registry = new BotCommandRegistry(List.of(new AiTestHandler()), null);

        assertNull(registry.find("/ai", true));
        assertNotNull(registry.find("/ai@any_bot", true));
    }

    @Test
    void shouldFailFastWhenCommandIsDuplicated() {
        assertThrows(IllegalStateException.class, () -> registry(
                new PingTestHandler(),
                new DuplicatePingTestHandler()
        ));
    }

    @Test
    void shouldFailFastWhenBotCommandAnnotationIsMissing() {
        assertThrows(IllegalStateException.class, () -> registry(
                new UnannotatedTestHandler()
        ));
    }

    @Test
    void shouldReturnDescribedCommandsSortedAndSkipBlankDescription() {
        BotCommandRegistry registry = registry(
                new StartTestHandler(),
                new PingTestHandler()
        );

        assertEquals(
                List.of(
                        new BotCommandRegistry.CommandInfo("/ping", "test"),
                        new BotCommandRegistry.CommandInfo("/start", "初始化并展示用户信息")
                ),
                registry.describedCommands()
        );
    }

    @Test
    void shouldDisplayMentionOnlyCommandWithBotUsernameInHelp() {
        BotCommandRegistry registry = registry(
                new StartTestHandler(),
                new AiTestHandler()
        );

        assertEquals(
                List.of(
                        new BotCommandRegistry.CommandInfo("/ai@test_bot", "调用 AI"),
                        new BotCommandRegistry.CommandInfo("/start", "初始化并展示用户信息")
                ),
                registry.describedCommands()
        );
    }

    @BotCommand(value = "/ping", description = "test")
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

    @BotCommand(value = "/ai", description = "调用 AI", groupRequireMention = true)
    private static class AiTestHandler implements BotCommandHandler {
        @Override
        public void execute(BotContext context) {
        }
    }
}
