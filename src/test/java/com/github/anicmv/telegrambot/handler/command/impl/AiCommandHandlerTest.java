package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.utils.BotUtil;
import com.github.anicmv.telegrambot.service.AiAccessControlService;
import com.github.anicmv.telegrambot.service.DeepSeekChatService;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import com.github.anicmv.telegrambot.config.BotProperties;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCommandHandlerTest {

    private static AiAccessControlService accessControlService(Long... blockedUserIds) {
        BotProperties properties = new BotProperties();
        for (Long blockedUserId : blockedUserIds) {
            properties.getAi().getBlacklistUserIds().add(blockedUserId);
        }
        return new AiAccessControlService(properties);
    }

    private static BotProperties botProperties(boolean autoDeleteEnabled, long autoDeleteDelaySeconds) {
        BotProperties properties = new BotProperties();
        properties.getAi().setAutoDeleteEnabled(autoDeleteEnabled);
        properties.getAi().setAutoDeleteDelaySeconds(autoDeleteDelaySeconds);
        return properties;
    }

    @Test
    void shouldExtractPromptAfterCommand() {
        assertEquals("帮我写周报", BotUtil.commandArgument("/ai 帮我写周报"));
        assertEquals("总结今天完成项", BotUtil.commandArgument("/ai@demo_bot   总结今天完成项"));
        assertEquals("", BotUtil.commandArgument("/ai"));
    }

    @Test
    void shouldResolvePromptFromReplyMessageWhenCommandHasNoArgs() {
        Message replied = mock(Message.class);
        Message message = mock(Message.class);
        when(replied.getText()).thenReturn("这条被回复的消息");
        when(message.getReplyToMessage()).thenReturn(replied);
        BotContext context = new BotContext(null, UpdateType.MESSAGE, 123L, 1L, "/ai", message, null, null, null);

        assertEquals("这条被回复的消息", AiCommandHandler.resolvePrompt(context));
    }

    private static AiCommandHandler handler(Messenger messenger,
                                            AiAccessControlService accessControlService,
                                            DeepSeekChatService deepSeekChatService,
                                            BotProperties properties,
                                            TaskScheduler taskScheduler) {
        TaskExecutor directExecutor = Runnable::run;
        return new AiCommandHandler(
                messenger,
                accessControlService,
                deepSeekChatService,
                properties,
                taskScheduler,
                directExecutor
        );
    }

    @Test
    void shouldReplyWithDeepSeekAnswer() {
        Messenger messenger = mock(Messenger.class);
        DeepSeekChatService deepSeekChatService = mock(DeepSeekChatService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        AiCommandHandler handler = handler(
                messenger,
                accessControlService(),
                deepSeekChatService,
                botProperties(true, 30),
                taskScheduler
        );
        Message message = mock(Message.class);
        when(message.getMessageId()).thenReturn(99);
        when(deepSeekChatService.chat("帮我写周报")).thenReturn("这是总结");
        when(messenger.sendReplyTextAndReturnMessageId(123L, 99, "🤖 正在思考...")).thenReturn(199);
        BotContext context = new BotContext(null, UpdateType.MESSAGE, 123L, 1L, "/ai 帮我写周报", message, null, null, null);

        handler.execute(context);

        verify(messenger).editMessageText(123L, 199, ">帮我写周报\n\n>这是总结", "MarkdownV2");
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void shouldAskDeepSeekWithReplyMessageWhenCommandHasNoArgs() {
        Messenger messenger = mock(Messenger.class);
        DeepSeekChatService deepSeekChatService = mock(DeepSeekChatService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        AiCommandHandler handler = handler(
                messenger,
                accessControlService(),
                deepSeekChatService,
                botProperties(false, 30),
                taskScheduler
        );
        Message replied = mock(Message.class);
        Message message = mock(Message.class);
        when(message.getMessageId()).thenReturn(100);
        when(replied.getText()).thenReturn("帮我解释这句话");
        when(message.getReplyToMessage()).thenReturn(replied);
        when(deepSeekChatService.chat("帮我解释这句话")).thenReturn("这是解释");
        when(messenger.sendReplyTextAndReturnMessageId(123L, 100, "🤖 正在思考...")).thenReturn(200);
        BotContext context = new BotContext(null, UpdateType.MESSAGE, 123L, 1L, "/ai", message, null, null, null);

        handler.execute(context);

        verify(messenger).editMessageText(123L, 200, ">帮我解释这句话\n\n>这是解释", "MarkdownV2");
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void shouldFallbackWhenCommandHasNoArgsAndNoReplyMessage() {
        Messenger messenger = mock(Messenger.class);
        DeepSeekChatService deepSeekChatService = mock(DeepSeekChatService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        AiCommandHandler handler = handler(
                messenger,
                accessControlService(),
                deepSeekChatService,
                botProperties(false, 30),
                taskScheduler
        );
        Message message = mock(Message.class);
        when(message.getMessageId()).thenReturn(101);
        when(deepSeekChatService.chat("")).thenReturn("请输入问题，例如：/ai 帮我总结今天的工作。");
        when(messenger.sendReplyTextAndReturnMessageId(123L, 101, "🤖 正在思考...")).thenReturn(201);
        BotContext context = new BotContext(null, UpdateType.MESSAGE, 123L, 1L, "/ai", message, null, null, null);

        handler.execute(context);

        verify(messenger).editMessageText(
                123L, 201, ">请输入问题，例如：/ai 帮我总结今天的工作。", "MarkdownV2");
    }

    @Test
    void shouldRejectBlockedUser() {
        Messenger messenger = mock(Messenger.class);
        DeepSeekChatService deepSeekChatService = mock(DeepSeekChatService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        AiCommandHandler handler = handler(
                messenger,
                accessControlService(2L),
                deepSeekChatService,
                botProperties(false, 30),
                taskScheduler
        );
        Message message = mock(Message.class);
        when(message.getMessageId()).thenReturn(102);
        when(messenger.sendReplyMarkdownV2TextAndReturnMessageId(
                123L,
                102,
                ">帮我写周报\n\n>你没有权限使用 AI 功能。"
        )).thenReturn(202);
        BotContext context = new BotContext(null, UpdateType.MESSAGE, 123L, 2L, "/ai 帮我写周报", message, null, null, null);

        handler.execute(context);

        verify(messenger).sendReplyMarkdownV2TextAndReturnMessageId(
                123L,
                102,
                ">帮我写周报\n\n>你没有权限使用 AI 功能。"
        );
        verify(deepSeekChatService, never()).chat("帮我写周报");
    }

    @Test
    void shouldFormatMarkdownV2ResponseWithQuote() {
        assertEquals(
                ">原问题<1\\>\n>第二行\n\n>回答&结果",
                AiCommandHandler.formatMarkdownV2Response("原问题<1>\n第二行", "回答&结果")
        );
    }

    @Test
    void shouldFormatLongAnswerAsExpandableQuote() {
        String longAnswer = "a".repeat(601);

        assertEquals(
                ">问题\n\n>" + longAnswer + "||",
                AiCommandHandler.formatMarkdownV2Response("问题", longAnswer)
        );
    }
}
