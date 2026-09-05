package com.github.anicmv.telegrambot.handler.command.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.ChatFullInfo;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class InfoCommandHandlerTest {

    @Mock
    private Messenger messenger;

    private InfoCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new InfoCommandHandler(messenger);
    }

    @Test
    void shouldFormatUserInfoWhenReplyingInGroup() {
        User target = User.builder().id(948540601L).firstName("猛男").lastName("可乐")
                .userName("aglluo").isBot(false).build();
        Message replied = new Message();
        replied.setFrom(target);
        Message command = command(7L, 999L);
        command.setReplyToMessage(replied);

        ChatFullInfo profile = org.mockito.Mockito.mock(ChatFullInfo.class);
        when(profile.getBio()).thenReturn("特征：是猛男");
        when(messenger.getChatFullInfo(948540601L)).thenReturn(profile);

        handler.execute(context("/info", command));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger).sendReplyHtmlText(eq(-100237L), eq(7), captor.capture());
        String text = captor.getValue();
        assertTrue(text.contains("🆔 ID: 948540601"));
        assertTrue(text.contains("🎨 昵称: 猛男 可乐"));
        assertTrue(text.contains("@aglluo"));
        assertTrue(text.contains("👥 所在群聊 ID: -100237"));
        assertTrue(text.contains("特征：是猛男"));
    }

    @Test
    void shouldOmitGroupLineWhenPrivateChat() {
        User target = User.builder().id(555L).firstName("私聊用户").isBot(false).build();
        Message command = new Message();
        command.setMessageId(3);
        command.setFrom(target);
        when(messenger.getChatFullInfo(555L)).thenReturn(null);

        handler.execute(new BotContext(null, UpdateType.MESSAGE, 555L, 555L, "/info", command, null, null, null));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger).sendHtmlText(eq(555L), captor.capture());
        assertTrue(captor.getValue().contains("私聊用户"));
        org.junit.jupiter.api.Assertions.assertFalse(captor.getValue().contains("所在群聊 ID"));
    }

    @Test
    void shouldFormatGroupInfoWhenNoReplyInGroup() {
        Message command = command(8L, 999L);
        ChatFullInfo group = org.mockito.Mockito.mock(ChatFullInfo.class);
        when(group.getId()).thenReturn(-100237L);
        when(group.getTitle()).thenReturn("测试群");
        when(group.getType()).thenReturn("supergroup");
        when(group.getDescription()).thenReturn("群简介 here");
        when(messenger.getChatFullInfo(-100237L)).thenReturn(group);

        handler.execute(context("/info", command));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger).sendHtmlText(eq(-100237L), captor.capture());
        String text = captor.getValue();
        assertTrue(text.contains("🆔 ID: -100237"));
        assertTrue(text.contains("📌 类型: 超级群"));
        assertTrue(text.contains("群简介 here"));
    }

    @Test
    void shouldDegradeGracefullyWhenChatInfoUnavailable() {
        Message command = command(9L, 999L);
        when(messenger.getChatFullInfo(-100237L)).thenReturn(null);

        handler.execute(context("/info", command));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger).sendHtmlText(eq(-100237L), captor.capture());
        assertTrue(captor.getValue().contains("无法获取该会话信息"));
    }

    private Message command(long messageId, long senderId) {
        Message message = new Message();
        message.setMessageId((int) messageId);
        message.setFrom(User.builder().id(senderId).firstName("caller").isBot(false).build());
        return message;
    }

    private BotContext context(String text, Message message) {
        return new BotContext(null, UpdateType.MESSAGE, -100237L, 999L, text, message, null, null, null);
    }
}
