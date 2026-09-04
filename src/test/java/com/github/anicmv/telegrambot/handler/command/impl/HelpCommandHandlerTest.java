package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.handler.command.BotCommandRegistry;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HelpCommandHandlerTest {

    @Test
    void shouldSendAggregatedHelpTextWithDescribedCommandsOnly() {
        Messenger messenger = mock(Messenger.class);
        BotCommandRegistry registry = new BotCommandRegistry(List.of(new PingTestHandler(), new HiddenTestHandler()));
        HelpCommandHandler handler = new HelpCommandHandler(messenger, registry);
        BotContext context = new BotContext(null, UpdateType.MESSAGE, 1L, 2L, "/help", null, null, null, null);

        handler.execute(context);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger).sendText(eq(1L), captor.capture());
        String text = captor.getValue();
        assertTrue(text.contains("/ping 连通性测试"));
        assertTrue(text.contains("@你的Bot kfc / pyq / du / top / xp / husband / bili / ecy"));
        assertFalse(text.contains("/test"));
    }

    @Test
    void shouldReplyWhenTriggeredByMessage() {
        Messenger messenger = mock(Messenger.class);
        BotCommandRegistry registry = new BotCommandRegistry(List.of(new PingTestHandler()));
        HelpCommandHandler handler = new HelpCommandHandler(messenger, registry);
        Message message = mock(Message.class);
        when(message.getMessageId()).thenReturn(5);
        BotContext context = new BotContext(null, UpdateType.MESSAGE, 1L, 2L, "/help", message, null, null, null);

        handler.execute(context);

        verify(messenger).sendReplyText(eq(1L), eq(5), org.mockito.ArgumentMatchers.anyString());
    }

    @BotCommand(value = "/ping", description = "连通性测试")
    private static class PingTestHandler implements BotCommandHandler {
        @Override
        public void execute(BotContext context) {
        }
    }

    @BotCommand("/test")
    private static class HiddenTestHandler implements BotCommandHandler {
        @Override
        public void execute(BotContext context) {
        }
    }
}
