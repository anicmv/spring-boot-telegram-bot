package com.github.anicmv.telegrambot.application.handler.inline;

import com.github.anicmv.telegrambot.service.AiAccessControlService;
import com.github.anicmv.telegrambot.service.DeepSeekChatService;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.handler.inline.chosen.impl.DeepSeekChosenInlineQueryHandler;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.telegram.telegrambots.meta.api.objects.inlinequery.ChosenInlineQuery;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeepSeekChosenBotInlineQueryHandlerTest {

    private static AiAccessControlService accessControlService(Long... blockedUserIds) {
        BotProperties properties = new BotProperties();
        for (Long blockedUserId : blockedUserIds) {
            properties.getAi().getBlacklistUserIds().add(blockedUserId);
        }
        return new AiAccessControlService(properties);
    }

    @Test
    void shouldEditInlineMessageWithMarkdownV2Answer() {
        Messenger messenger = mock(Messenger.class);
        DeepSeekChatService deepSeekChatService = mock(DeepSeekChatService.class);
        TaskExecutor taskExecutor = Runnable::run;
        DeepSeekChosenInlineQueryHandler handler = new DeepSeekChosenInlineQueryHandler(
                messenger,
                accessControlService(),
                deepSeekChatService,
                taskExecutor
        );
        ChosenInlineQuery chosenInlineQuery = mock(ChosenInlineQuery.class);
        when(chosenInlineQuery.getResultId()).thenReturn("N_14");
        when(chosenInlineQuery.getInlineMessageId()).thenReturn("inline-msg-1");
        when(deepSeekChatService.chat("你好")).thenReturn("你好 *世界*");
        BotContext context = new BotContext(null, UpdateType.CHOSEN_INLINE_QUERY, null, 1L, "ai 你好", null, null, null, chosenInlineQuery);

        handler.execute(context);

        verify(messenger).editInlineMessageText(
                eq("inline-msg-1"),
                eq(">你好\n\n>你好 \\*世界\\*"),
                eq("MarkdownV2"),
                eq(true)
        );
    }

    @Test
    void shouldEscapeTelegramMarkdownV2SpecialCharacters() {
        Messenger messenger = mock(Messenger.class);
        DeepSeekChatService deepSeekChatService = mock(DeepSeekChatService.class);
        TaskExecutor taskExecutor = Runnable::run;
        DeepSeekChosenInlineQueryHandler handler = new DeepSeekChosenInlineQueryHandler(
                messenger,
                accessControlService(),
                deepSeekChatService,
                taskExecutor
        );
        ChosenInlineQuery chosenInlineQuery = mock(ChosenInlineQuery.class);
        when(chosenInlineQuery.getResultId()).thenReturn("N_14");
        when(chosenInlineQuery.getInlineMessageId()).thenReturn("inline-msg-2");
        when(deepSeekChatService.chat("苏州自助推荐哪家")).thenReturn("1. **金钱豹国际美食百汇**（多家分店）");
        BotContext context = new BotContext(null, UpdateType.CHOSEN_INLINE_QUERY, null, 1L, "ai 苏州自助推荐哪家", null, null, null, chosenInlineQuery);

        handler.execute(context);

        verify(messenger).editInlineMessageText(
                eq("inline-msg-2"),
                eq(">苏州自助推荐哪家\n\n>1\\. \\*\\*金钱豹国际美食百汇\\*\\*（多家分店）"),
                eq("MarkdownV2"),
                eq(true)
        );
    }

    @Test
    void shouldStripCommonMarkdownMarkersBeforeEscaping() {
        Messenger messenger = mock(Messenger.class);
        DeepSeekChatService deepSeekChatService = mock(DeepSeekChatService.class);
        TaskExecutor taskExecutor = Runnable::run;
        DeepSeekChosenInlineQueryHandler handler = new DeepSeekChosenInlineQueryHandler(
                messenger,
                accessControlService(),
                deepSeekChatService,
                taskExecutor
        );
        ChosenInlineQuery chosenInlineQuery = mock(ChosenInlineQuery.class);
        when(chosenInlineQuery.getResultId()).thenReturn("N_14");
        when(chosenInlineQuery.getInlineMessageId()).thenReturn("inline-msg-3");
        when(deepSeekChatService.chat("苏州自助推荐")).thenReturn("1. **香格里拉大酒店·咖啡亭**（高新区）—— 海鲜、刺身品种丰富");

        BotContext context = new BotContext(null, UpdateType.CHOSEN_INLINE_QUERY, null, 1L, "ai 苏州自助推荐", null, null, null, chosenInlineQuery);

        handler.execute(context);

        verify(messenger).editInlineMessageText(
                eq("inline-msg-3"),
                eq(">苏州自助推荐\n\n>1\\. \\*\\*香格里拉大酒店·咖啡亭\\*\\*（高新区）—— 海鲜、刺身品种丰富"),
                eq("MarkdownV2"),
                eq(true)
        );
    }

    @Test
    void shouldRejectBlockedUserBeforeCallingDeepSeek() {
        Messenger messenger = mock(Messenger.class);
        DeepSeekChatService deepSeekChatService = mock(DeepSeekChatService.class);
        TaskExecutor taskExecutor = Runnable::run;
        DeepSeekChosenInlineQueryHandler handler = new DeepSeekChosenInlineQueryHandler(
                messenger,
                accessControlService(2L),
                deepSeekChatService,
                taskExecutor
        );
        ChosenInlineQuery chosenInlineQuery = mock(ChosenInlineQuery.class);
        when(chosenInlineQuery.getResultId()).thenReturn("N_14");
        when(chosenInlineQuery.getInlineMessageId()).thenReturn("inline-msg-4");
        BotContext context = new BotContext(null, UpdateType.CHOSEN_INLINE_QUERY, null, 2L, "ai 你好", null, null, null, chosenInlineQuery);

        handler.execute(context);

        verify(messenger).editInlineMessageText(
                eq("inline-msg-4"),
                eq(">你好\n\n>你没有权限使用 AI 功能。"),
                eq("MarkdownV2"),
                eq(true)
        );
        verify(deepSeekChatService, never()).chat("你好");
    }
}
