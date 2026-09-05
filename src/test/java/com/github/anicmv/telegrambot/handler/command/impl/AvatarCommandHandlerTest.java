package com.github.anicmv.telegrambot.handler.command.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class AvatarCommandHandlerTest {

    @Mock
    private Messenger messenger;

    private AvatarCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AvatarCommandHandler(messenger);
    }

    @Test
    void shouldFetchRepliedUserAvatarsWhenReplying() {
        Message replyTo = new Message();
        replyTo.setFrom(user(201L, "target_guy"));
        Message command = new Message();
        command.setMessageId(7);
        command.setFrom(user(999L, "caller"));
        command.setReplyToMessage(replyTo);

        when(messenger.getUserAvatarFileIds(201L)).thenReturn(List.of("file_a", "file_b"));

        handler.execute(context("/avatars", command));

        verify(messenger).sendPhotoAlbumByFileIds(eq(-100123L), eq(7), eq(List.of("file_a", "file_b")),
                org.mockito.ArgumentMatchers.argThat(caption -> caption.contains("@target_guy") && caption.contains("2")));
    }

    @Test
    void shouldFetchOwnAvatarsWhenNotReplying() {
        Message command = new Message();
        command.setMessageId(8);
        command.setFrom(user(999L, "caller"));

        when(messenger.getUserAvatarFileIds(999L)).thenReturn(List.of("file_me"));

        handler.execute(context("/avatars", command));

        verify(messenger).sendPhotoAlbumByFileIds(eq(-100123L), eq(8), eq(List.of("file_me")), anyString());
    }

    @Test
    void shouldNotifyWhenNoAvatars() {
        Message command = new Message();
        command.setMessageId(9);
        command.setFrom(user(999L, null));

        when(messenger.getUserAvatarFileIds(999L)).thenReturn(List.of());

        handler.execute(context("/avatars", command));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger).sendText(eq(-100123L), captor.capture());
        assertTrue(captor.getValue().contains("没有可用的头像"));
        verify(messenger, never()).sendPhotoAlbumByFileIds(anyLong(), any(), anyList(), anyString());
    }

    @Test
    void displayNameShouldFallBackToFirstName() {
        User noUsername = User.builder().id(1L).firstName("小明").isBot(false).build();
        Message command = new Message();
        command.setMessageId(1);
        command.setFrom(noUsername);
        when(messenger.getUserAvatarFileIds(1L)).thenReturn(List.of("f"));

        handler.execute(context("/avatars", command));

        ArgumentCaptor<String> caption = ArgumentCaptor.forClass(String.class);
        verify(messenger).sendPhotoAlbumByFileIds(eq(-100123L), eq(1), anyList(), caption.capture());
        assertTrue(caption.getValue().contains("小明"));
    }

    private BotContext context(String text, Message message) {
        return new BotContext(null, UpdateType.MESSAGE, -100123L, 999L, text, message, null, null, null);
    }

    private User user(long id, String username) {
        return User.builder()
                .id(id)
                .userName(username)
                .firstName(username == null ? "" : username)
                .isBot(false)
                .build();
    }
}
