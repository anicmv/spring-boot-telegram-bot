package com.github.anicmv.telegrambot.handler.command.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.entity.UserProfileEntity;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.BotUserProfile;
import com.github.anicmv.telegrambot.model.UpdateType;
import com.github.anicmv.telegrambot.repository.BotUserRepository;
import com.github.anicmv.telegrambot.repository.UserProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileCommandHandlerTest {

    @Mock
    private Messenger messenger;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private BotUserRepository botUserRepository;

    private BotProperties properties;
    private ProfileCommandHandler handler;

    @BeforeEach
    void setUp() {
        properties = new BotProperties();
        handler = new ProfileCommandHandler(messenger, userProfileRepository, botUserRepository,
                properties, new ObjectMapper(), Runnable::run);
    }

    @Test
    void shouldShowFriendlyTextWhenNoProfile() {
        when(messenger.sendReplyTextAndReturnMessageId(eq(-100123L), eq(7), anyString())).thenReturn(700);
        when(userProfileRepository.findByTelegramId(999L)).thenReturn(Optional.empty());

        handler.execute(context("/profile"));

        verify(userProfileRepository).findByTelegramId(999L);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger).editMessageText(eq(-100123L), eq(700), captor.capture(), eq("MarkdownV2"));
        assertTrue(captor.getValue().contains("没有画像数据"));
    }

    @Test
    void shouldFormatExistingProfile() {
        UserProfileEntity profile = new UserProfileEntity();
        profile.setTelegramUserId(999L);
        profile.setSummary("热爱二次元");
        profile.setInterests("[\"动漫\",\"游戏\"]");
        profile.setPersonality("{\"开朗\":\"活跃\"}");
        profile.setActiveHours("深夜");
        profile.setFrequentTopics("[\"新番\"]");
        profile.setAnalyzedMessageCount(42);
        when(messenger.sendReplyTextAndReturnMessageId(eq(-100123L), eq(7), anyString())).thenReturn(700);
        when(userProfileRepository.findByTelegramId(999L)).thenReturn(Optional.of(profile));

        handler.execute(context("/profile"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger).editMessageText(eq(-100123L), eq(700), captor.capture(), eq("MarkdownV2"));
        String text = captor.getValue();
        assertTrue(text.contains("用户画像"));
        assertTrue(text.contains("热爱二次元"));
        assertTrue(text.contains("动漫"));
        assertTrue(text.contains("游戏"));
        assertTrue(text.contains("深夜"));
        assertTrue(text.contains("新番"));
        assertTrue(text.contains("42"));
    }

    @Test
    void queryingOthersByUsernameShouldBeRejectedByDefault() {
        handler.execute(context("/profile @someone"));

        verify(userProfileRepository, never()).findByTelegramId(anyLong());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(messenger).sendReplyText(eq(-100123L), eq(7), captor.capture());
        assertTrue(captor.getValue().contains("暂未开启"));
    }

    @Test
    void queryingOthersShouldWorkWhenEnabled() {
        properties.getProfile().setAllowQueryOthers(true);
        when(botUserRepository.findByUsername("someone"))
                .thenReturn(Optional.of(new BotUserProfile(1L, "someone", "某人", 888L, null, null)));
        when(messenger.sendReplyTextAndReturnMessageId(eq(-100123L), eq(7), anyString())).thenReturn(700);
        when(userProfileRepository.findByTelegramId(888L)).thenReturn(Optional.empty());

        handler.execute(context("/profile @someone"));

        verify(userProfileRepository).findByTelegramId(888L);
    }

    @Test
    void extractFirstArgumentShouldParseCommandArgument() {
        assertEquals("@abc", ProfileCommandHandler.extractFirstArgument("/profile @abc"));
        assertEquals("", ProfileCommandHandler.extractFirstArgument("/profile"));
        assertEquals("", ProfileCommandHandler.extractFirstArgument(null));
    }

    private BotContext context(String text) {
        Message message = new Message();
        message.setMessageId(7);
        return new BotContext(null, UpdateType.MESSAGE, -100123L, 999L, text, message, null, null, null);
    }
}
