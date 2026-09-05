package com.github.anicmv.telegrambot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.entity.ChatMessageEntity;
import com.github.anicmv.telegrambot.entity.UserProfileEntity;
import com.github.anicmv.telegrambot.model.ProfileAnalysisStats;
import com.github.anicmv.telegrambot.repository.ChatMessageRepository;
import com.github.anicmv.telegrambot.repository.UserProfileRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileAnalysisServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private DeepSeekChatService deepSeekChatService;

    private BotProperties properties;
    private ProfileAnalysisService service;
    private List<String> progressLogs;

    @BeforeEach
    void setUp() {
        properties = new BotProperties();
        properties.getProfile().getRecordGroupIds().add(-100L);
        service = new ProfileAnalysisService(chatMessageRepository, userProfileRepository,
                deepSeekChatService, properties, new ObjectMapper());
        progressLogs = new ArrayList<>();
    }

    @Test
    void emptyWhitelistShouldSkipEverything() {
        properties.getProfile().getRecordGroupIds().clear();

        ProfileAnalysisStats stats = service.analyzeAll(progressLogs::add);

        assertEquals(new ProfileAnalysisStats(0, 0, 0, 0), stats);
        verifyNoInteractions(chatMessageRepository, deepSeekChatService, userProfileRepository);
    }

    @Test
    void shouldSaveProfileAndAdvanceCursor() {
        when(chatMessageRepository.findDistinctUserIdsWithNewMessages(any())).thenReturn(List.of(1L));
        when(userProfileRepository.findByTelegramId(1L)).thenReturn(Optional.empty());
        when(chatMessageRepository.findNewerThanByUser(any(), eq(1L), eq(0L), anyInt()))
                .thenReturn(List.of(message(5L, "我喜欢打游戏"), message(6L, "今晚开黑吗")));
        when(deepSeekChatService.chatWithUsage(anyString(), anyString())).thenReturn(
                new DeepSeekChatService.ChatResult(
                        "{\"summary\":\"爱打游戏\",\"report\":\"白天摸鱼晚上开黑的典型群友。\\n\\n第二段。\"}", 1234L));

        ProfileAnalysisStats stats = service.analyzeAll(progressLogs::add);

        assertEquals(new ProfileAnalysisStats(1, 1, 0, 0), stats);
        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).upsert(captor.capture());
        UserProfileEntity saved = captor.getValue();
        assertEquals(1L, saved.getTelegramUserId());
        assertEquals("爱打游戏", saved.getSummary());
        assertTrue(saved.getReport().contains("开黑"));
        assertEquals(1234L, saved.getTotalTokens());
        assertEquals(6L, saved.getLastAnalyzedMessageId());
        assertEquals(2, saved.getAnalyzedMessageCount());
        assertEquals("deepseek-chat", saved.getModel());
    }

    @Test
    void parseFailureShouldAdvanceCursorOnlyAndCountFailed() {
        when(chatMessageRepository.findDistinctUserIdsWithNewMessages(any())).thenReturn(List.of(1L));
        when(userProfileRepository.findByTelegramId(1L)).thenReturn(Optional.empty());
        when(chatMessageRepository.findNewerThanByUser(any(), eq(1L), eq(0L), anyInt()))
                .thenReturn(List.of(message(7L, "随便聊聊")));
        when(deepSeekChatService.chatWithUsage(anyString(), anyString())).thenReturn(
                new DeepSeekChatService.ChatResult("这不是 JSON", 5L));

        ProfileAnalysisStats stats = service.analyzeAll(progressLogs::add);

        assertEquals(new ProfileAnalysisStats(1, 0, 1, 0), stats);
        verify(deepSeekChatService, times(2)).chatWithUsage(anyString(), anyString());
        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).upsert(captor.capture());
        UserProfileEntity saved = captor.getValue();
        assertEquals(7L, saved.getLastAnalyzedMessageId());
        assertNull(saved.getSummary());
    }

    @Test
    void noNewMessagesShouldSkip() {
        when(chatMessageRepository.findDistinctUserIdsWithNewMessages(any())).thenReturn(List.of(1L));
        when(userProfileRepository.findByTelegramId(1L)).thenReturn(Optional.empty());
        when(chatMessageRepository.findNewerThanByUser(any(), eq(1L), eq(0L), anyInt()))
                .thenReturn(List.of());

        ProfileAnalysisStats stats = service.analyzeAll(progressLogs::add);

        assertEquals(new ProfileAnalysisStats(1, 0, 0, 1), stats);
        verify(deepSeekChatService, never()).chatWithUsage(anyString(), anyString());
        verify(userProfileRepository, never()).upsert(any());
    }

    @Test
    void shouldMergeOldProfileCursor() {
        UserProfileEntity oldProfile = new UserProfileEntity();
        oldProfile.setId(11L);
        oldProfile.setTelegramUserId(1L);
        oldProfile.setLastAnalyzedMessageId(100L);
        oldProfile.setAnalyzedMessageCount(50);
        when(chatMessageRepository.findDistinctUserIdsWithNewMessages(any())).thenReturn(List.of(1L));
        when(userProfileRepository.findByTelegramId(1L)).thenReturn(Optional.of(oldProfile));
        when(chatMessageRepository.findNewerThanByUser(any(), eq(1L), eq(100L), anyInt()))
                .thenReturn(List.of(message(101L, "新消息")));
        when(deepSeekChatService.chatWithUsage(anyString(), anyString())).thenReturn(
                new DeepSeekChatService.ChatResult("{\"summary\":\"更新后的画像\",\"report\":\"新的正文\"}", 8L));

        ProfileAnalysisStats stats = service.analyzeAll(progressLogs::add);

        assertEquals(new ProfileAnalysisStats(1, 1, 0, 0), stats);
        ArgumentCaptor<UserProfileEntity> captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).upsert(captor.capture());
        UserProfileEntity saved = captor.getValue();
        assertEquals(11L, saved.getId());
        assertEquals(101L, saved.getLastAnalyzedMessageId());
        assertEquals(51, saved.getAnalyzedMessageCount());
        assertEquals(8L, saved.getTotalTokens());
    }

    @Test
    void shouldAnalyzeUsersInParallel() {
        properties.getProfile().setAnalysisConcurrency(3);
        when(chatMessageRepository.findDistinctUserIdsWithNewMessages(any())).thenReturn(List.of(1L, 2L, 3L));
        when(userProfileRepository.findByTelegramId(any())).thenReturn(Optional.empty());
        when(chatMessageRepository.findNewerThanByUser(any(), any(), eq(0L), anyInt()))
                .thenReturn(List.of(message(5L, "消息内容")));
        when(deepSeekChatService.chatWithUsage(anyString(), anyString())).thenAnswer(invocation -> {
            Thread.sleep(150);
            return new DeepSeekChatService.ChatResult("{\"summary\":\"s\",\"report\":\"r\"}", 1L);
        });

        long startNanos = System.nanoTime();
        ProfileAnalysisStats stats = service.analyzeAll(progressLogs::add);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertEquals(new ProfileAnalysisStats(3, 3, 0, 0), stats);
        // 串行至少 450ms，并行应在其一半以内完成（留足调度余量）
        assertTrue(elapsedMillis < 350, "expected parallel execution, took " + elapsedMillis + "ms");
    }

    @Test
    void stripCodeFenceShouldRemoveMarkdownFence() {
        assertEquals("{\"a\":1}", ProfileAnalysisService.stripCodeFence("```json\n{\"a\":1}\n```"));
        assertEquals("{\"a\":1}", ProfileAnalysisService.stripCodeFence("{\"a\":1}"));
    }

    private ChatMessageEntity message(long id, String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(id);
        entity.setChatId(-100L);
        entity.setTelegramUserId(1L);
        entity.setMessageType("text");
        entity.setContent(content);
        entity.setSentAt(LocalDateTime.of(2026, 9, 3, 21, 30));
        return entity;
    }
}
