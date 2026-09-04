package com.github.anicmv.telegrambot.handler.inline.chosen.impl;

import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.BotUserProfile;
import com.github.anicmv.telegrambot.model.UpdateType;
import com.github.anicmv.telegrambot.service.BotUserProfileService;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.inlinequery.ChosenInlineQuery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchmakerChosenInlineQueryHandlerTest {

    private static final String INLINE_MESSAGE_ID = "inline-1";

    @Mock
    private Messenger messenger;

    @Mock
    private BotUserProfileService botUserProfileService;

    private BotProperties properties;

    @BeforeEach
    void setUp() {
        properties = new BotProperties();
        properties.setChannelId(-5L);
    }

    @Test
    void shouldUseFreshAvatarFileIdAndPersist() {
        stubRandom(profile(2L, "old-id", null));
        when(messenger.getUserAvatarFileId(2L)).thenReturn("new-id");
        stubEdit(true);

        newHandler().execute(context());

        verify(messenger).editInlineMessagePhoto(eq(INLINE_MESSAGE_ID), eq("new-id"), anyString(), eq("MarkdownV2"));
        verify(botUserProfileService).upsert(argThat(p -> "new-id".equals(p.avatarFileId()) && p.avatarData() == null));
    }

    @Test
    void shouldReuploadAvatarDataWhenNoProfilePhoto() {
        byte[] avatarData = new byte[]{1, 2};
        stubRandom(profile(2L, "old-id", avatarData));
        when(messenger.getUserAvatarFileId(2L)).thenReturn(null);
        when(messenger.uploadPhotoBytes(-5L, avatarData)).thenReturn("up-id");
        stubEdit(true);

        newHandler().execute(context());

        verify(messenger).editInlineMessagePhoto(eq(INLINE_MESSAGE_ID), eq("up-id"), anyString(), eq("MarkdownV2"));
        verify(botUserProfileService).upsert(argThat(p -> "up-id".equals(p.avatarFileId())));
    }

    @Test
    void shouldFallbackToStoredFileIdWhenNothingFresh() {
        stubRandom(profile(2L, "old-id", null));
        when(messenger.getUserAvatarFileId(2L)).thenReturn(null);
        stubEdit(true);

        newHandler().execute(context());

        verify(messenger).editInlineMessagePhoto(eq(INLINE_MESSAGE_ID), eq("old-id"), anyString(), eq("MarkdownV2"));
        verify(botUserProfileService, never()).upsert(any());
    }

    @Test
    void emptyPoolShouldEditPlaceholder() {
        when(botUserProfileService.findRandomWithAvatarExcluding(anyCollection())).thenReturn(Optional.empty());

        newHandler().execute(context());

        verify(messenger).editInlineMessagePhoto(eq(INLINE_MESSAGE_ID), anyString(),
                argThat(text -> text.contains("摸鱼")), any());
    }

    @Test
    void editFailureShouldRerollNextCandidate() {
        stubRandom(profile(2L, "old-id", null), profile(3L, "old3", null));
        when(messenger.getUserAvatarFileId(2L)).thenReturn(null);
        when(messenger.getUserAvatarFileId(3L)).thenReturn("fresh3");
        stubEdit(false, true);

        newHandler().execute(context());

        ArgumentCaptor<String> mediaCaptor = ArgumentCaptor.forClass(String.class);
        verify(messenger, times(2)).editInlineMessagePhoto(eq(INLINE_MESSAGE_ID), mediaCaptor.capture(), anyString(), eq("MarkdownV2"));
        assertEquals("old-id", mediaCaptor.getAllValues().get(0));
        assertEquals("fresh3", mediaCaptor.getAllValues().get(1));
        verify(botUserProfileService, times(1)).upsert(argThat(p -> "fresh3".equals(p.avatarFileId())));

        ArgumentCaptor<Collection<Long>> excludeCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(botUserProfileService, times(2)).findRandomWithAvatarExcluding(excludeCaptor.capture());
        assertTrue(excludeCaptor.getAllValues().get(1).contains(2L), "第二次应排除刚失败的人");
    }

    @Test
    void allEditsFailShouldShowRetryPlaceholder() {
        stubRandom(profile(2L, "old2", null), profile(3L, "old3", null), profile(4L, "old4", null));
        when(messenger.getUserAvatarFileId(anyLong())).thenReturn(null);

        newHandler().execute(context());

        ArgumentCaptor<String> captionCaptor = ArgumentCaptor.forClass(String.class);
        verify(messenger, times(4)).editInlineMessagePhoto(eq(INLINE_MESSAGE_ID), anyString(), captionCaptor.capture(), any());
        assertTrue(captionCaptor.getAllValues().get(3).contains("本轮"), "重摇耗尽应提示稍后再试");
    }

    private MatchmakerChosenInlineQueryHandler newHandler() {
        return new MatchmakerChosenInlineQueryHandler(messenger, botUserProfileService, properties);
    }

    private void stubRandom(BotUserProfile... profiles) {
        org.mockito.stubbing.OngoingStubbing<Optional<BotUserProfile>> stubbing =
                when(botUserProfileService.findRandomWithAvatarExcluding(anyCollection()));
        for (BotUserProfile profile : profiles) {
            stubbing = stubbing.thenReturn(Optional.of(profile));
        }
    }

    private void stubEdit(boolean... results) {
        org.mockito.stubbing.OngoingStubbing<Boolean> stubbing =
                when(messenger.editInlineMessagePhoto(any(), any(), any(), any()));
        for (boolean result : results) {
            stubbing = stubbing.thenReturn(result);
        }
    }

    private BotUserProfile profile(Long telegramId, String avatarFileId, byte[] avatarData) {
        return new BotUserProfile(10L, "user", "nick", telegramId, avatarFileId, avatarData);
    }

    private BotContext context() {
        User from = new User(1L, "Me", false);
        from.setUserName("me");
        ChosenInlineQuery query = ChosenInlineQuery.builder()
                .from(from)
                .query("/inline")
                .resultId("r1")
                .inlineMessageId(INLINE_MESSAGE_ID)
                .build();
        return new BotContext(null, UpdateType.CHOSEN_INLINE_QUERY, null, 1L, null, null, null, null, query);
    }
}
