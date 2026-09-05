package com.github.anicmv.telegrambot.handler.callback.impl;

import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.BotUserProfile;
import com.github.anicmv.telegrambot.model.InlineButton;
import com.github.anicmv.telegrambot.model.UpdateType;
import com.github.anicmv.telegrambot.repository.BotUserRepository;
import com.github.anicmv.telegrambot.repository.ProfileAllowUserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileAuthCallbackActionHandlerTest {

    private static final long ADMIN_ID = 1L;
    private static final long TARGET_ID = 999L;

    @Mock
    private Messenger messenger;

    @Mock
    private ProfileAllowUserRepository profileAllowUserRepository;

    @Mock
    private BotUserRepository botUserRepository;

    private BotProperties properties;
    private ProfileAuthCallbackActionHandler handler;

    @BeforeEach
    void setUp() {
        properties = new BotProperties();
        properties.getProfile().getAdminUserIds().add(ADMIN_ID);
        handler = new ProfileAuthCallbackActionHandler(messenger, profileAllowUserRepository,
                botUserRepository, properties);
    }

    @Test
    void adminApproveShouldPersistAndReplaceButtonsWithResult() {
        when(botUserRepository.findByTelegramId(ADMIN_ID))
                .thenReturn(Optional.of(new BotUserProfile(1L, "admin_col", null, ADMIN_ID, null, null)));
        when(botUserRepository.findByTelegramId(TARGET_ID)).thenReturn(Optional.empty());

        handler.execute(context(ADMIN_ID, "Y:" + TARGET_ID), "Y:" + TARGET_ID);

        verify(profileAllowUserRepository).approve(TARGET_ID, ADMIN_ID);
        verify(profileAllowUserRepository, never()).deny(anyLong());
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messenger).editMessageText(eq(-100123L), eq(500), textCaptor.capture(), isNull(), anyList());
        assertTrue(textCaptor.getValue().contains("已授权"));
        assertTrue(textCaptor.getValue().contains("@admin_col"));
        assertTrue(textCaptor.getValue().contains("ID " + TARGET_ID));
        verify(messenger).answerCallback("cb-1", "已授权");
    }

    @Test
    void adminDenyShouldPersistAndEditMessage() {
        handler.execute(context(ADMIN_ID, "N:" + TARGET_ID), "N:" + TARGET_ID);

        verify(profileAllowUserRepository).deny(TARGET_ID);
        verify(profileAllowUserRepository, never()).approve(anyLong(), anyLong());
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(messenger).editMessageText(eq(-100123L), eq(500), textCaptor.capture(), isNull(), anyList());
        assertTrue(textCaptor.getValue().contains("已拒绝"));
        verify(messenger).answerCallback("cb-1", "已拒绝");
    }

    @Test
    void clearedButtonsShouldBePassedAsEmptyList() {
        handler.execute(context(ADMIN_ID, "Y:" + TARGET_ID), "Y:" + TARGET_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InlineButton>> buttonsCaptor = ArgumentCaptor.forClass(List.class);
        verify(messenger).editMessageText(eq(-100123L), eq(500), anyString(), isNull(), buttonsCaptor.capture());
        assertTrue(buttonsCaptor.getValue().isEmpty());
    }

    @Test
    void nonAdminClickShouldBeRejectedWithoutTouchingDatabase() {
        handler.execute(context(222L, "Y:" + TARGET_ID), "Y:" + TARGET_ID);

        verify(profileAllowUserRepository, never()).approve(anyLong(), anyLong());
        verify(profileAllowUserRepository, never()).deny(anyLong());
        verify(messenger, never()).editMessageText(anyLong(), any(), anyString(), any(), any());
        verify(messenger).answerCallback("cb-1", "仅管理员可授权");
    }

    @Test
    void malformedPayloadShouldOnlyAnswerCallback() {
        handler.execute(context(ADMIN_ID, "MAYBE"), "MAYBE");

        verify(profileAllowUserRepository, never()).approve(anyLong(), anyLong());
        verify(messenger).answerCallback("cb-1", "按钮参数异常");
    }

    private BotContext context(Long clickerId, String data) {
        Message message = new Message();
        message.setMessageId(500);
        CallbackQuery query = new CallbackQuery();
        query.setId("cb-1");
        query.setFrom(User.builder().id(clickerId).firstName("clicker").isBot(false).build());
        query.setMessage(message);
        return new BotContext(null, UpdateType.CALLBACK_QUERY, -100123L, clickerId,
                "PROFILE_AUTH:" + data, null, query, null, null);
    }
}
