package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import com.github.anicmv.telegrambot.service.BangumiWorkTranslationService;
import com.github.anicmv.telegrambot.service.TraceMoeService;
import com.github.anicmv.telegrambot.service.TraceMoeService.AnimeResult;
import com.github.anicmv.telegrambot.service.TraceMoeService.SearchResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnimeSearchCommandHandlerTest {

    private static final Long CHAT_ID = -100123L;
    private static final Integer CMD_MESSAGE_ID = 7;
    private static final Integer PROGRESS_MESSAGE_ID = 99;

    @Mock
    private Messenger messenger;

    @Mock
    private TraceMoeService traceMoeService;

    @Test
    void noReplyShouldHint() {
        newHandler(Runnable::run).execute(context(commandMessage(null)));

        verify(messenger).sendReplyText(eq(CHAT_ID), eq(CMD_MESSAGE_ID), anyString());
        verifyNoInteractions(traceMoeService);
    }

    @Test
    void emptyDownloadedImageShouldEditProgressToError() {
        stubProgressReply();
        when(messenger.downloadFileBytes("large-file")).thenReturn(new byte[0]);

        newHandler(Runnable::run).execute(context(commandMessage(photoMessage())));

        verify(messenger).editMessageText(eq(CHAT_ID), eq(PROGRESS_MESSAGE_ID),
                argThat(text -> text.contains("获取图片失败")), eq("HTML"));
        verifyNoInteractions(traceMoeService);
    }

    @Test
    void shouldSendHighestSimilarityPreviewWithLinksInCaption() throws Exception {
        stubProgressReply();
        when(messenger.downloadFileBytes("large-file")).thenReturn(new byte[]{1});
        AnimeResult lower = result("低匹配", 0.91, "https://media.trace.moe/lower.mp4");
        AnimeResult highest = result("高匹配", 0.99, "https://media.trace.moe/highest.mp4");
        when(traceMoeService.search(any())).thenReturn(new SearchResponse(true, "ok", List.of(lower, highest)));
        Path preview = Files.write(Files.createTempFile("anime-preview-test-", ".mp4"), new byte[]{1});
        when(traceMoeService.downloadPreview(highest.previewUrl())).thenReturn(preview);
        when(messenger.sendReplyVideoByPath(eq(CHAT_ID), eq(CMD_MESSAGE_ID), eq(preview.toString()), anyString()))
                .thenReturn(true);
        ArgumentCaptor<String> captionCaptor = ArgumentCaptor.forClass(String.class);

        newHandler(Runnable::run).execute(context(commandMessage(photoMessage())));

        verify(traceMoeService).downloadPreview(highest.previewUrl());
        verify(messenger).sendReplyVideoByPath(eq(CHAT_ID), eq(CMD_MESSAGE_ID), eq(preview.toString()),
                captionCaptor.capture());
        String caption = captionCaptor.getValue();
        assertTrue(caption.contains(highest.previewUrl()));
        assertTrue(caption.contains(lower.previewUrl()));
        assertTrue(caption.indexOf("高匹配") < caption.indexOf("低匹配"));
        assertTrue(caption.length() <= 1024);
        verify(messenger).deleteMessageSilently(CHAT_ID, PROGRESS_MESSAGE_ID);
        // 一次"正在翻译标题"，一次"正在上传预览片段"
        verify(messenger, times(2)).editMessageText(eq(CHAT_ID), eq(PROGRESS_MESSAGE_ID), anyString(), eq("HTML"));
        assertFalse(Files.exists(preview));
    }

    @Test
    void previewDownloadFailureShouldFallbackToFullHtmlResult() {
        stubSuccessfulSearch();
        when(traceMoeService.downloadPreview("https://media.trace.moe/preview.mp4"))
                .thenThrow(new IllegalStateException("download failed"));

        newHandler(Runnable::run).execute(context(commandMessage(photoMessage())));

        verify(messenger, never()).sendReplyVideoByPath(any(), any(), anyString(), anyString());
        verify(messenger).editMessageText(eq(CHAT_ID), eq(PROGRESS_MESSAGE_ID),
                argThat(text -> text.contains("动漫识图结果")
                        && text.contains("https://media.trace.moe/preview.mp4")
                        && text.contains("预览片段")), eq("HTML"));
    }

    @Test
    void translatedTitleShouldAppearInResultHeader() {
        stubSuccessfulSearch();
        when(traceMoeService.downloadPreview("https://media.trace.moe/preview.mp4"))
                .thenThrow(new IllegalStateException("download failed"));

        newHandler(Runnable::run, Map.of("测试动画",
                        new BangumiWorkTranslationService.Translation(
                                "测试中文名", "https://bgm.tv/subject/123")))
                .execute(context(commandMessage(photoMessage())));

        verify(messenger).editMessageText(eq(CHAT_ID), eq(PROGRESS_MESSAGE_ID),
                argThat(text -> text.contains(
                        "<a href=\"https://bgm.tv/subject/123\">测试中文名</a>（测试动画）")), eq("HTML"));
    }

    @Test
    void failedVideoUploadShouldFallbackAndDeletePreview() throws Exception {
        stubSuccessfulSearch();
        Path preview = Files.write(Files.createTempFile("anime-preview-test-", ".mp4"), new byte[]{1});
        when(traceMoeService.downloadPreview("https://media.trace.moe/preview.mp4")).thenReturn(preview);
        when(messenger.sendReplyVideoByPath(eq(CHAT_ID), eq(CMD_MESSAGE_ID), eq(preview.toString()), anyString()))
                .thenReturn(false);

        newHandler(Runnable::run).execute(context(commandMessage(photoMessage())));

        verify(messenger).editMessageText(eq(CHAT_ID), eq(PROGRESS_MESSAGE_ID),
                argThat(text -> text.contains("动漫识图结果")
                        && text.contains("https://media.trace.moe/preview.mp4")), eq("HTML"));
        verify(messenger, never()).deleteMessageSilently(CHAT_ID, PROGRESS_MESSAGE_ID);
        assertFalse(Files.exists(preview));
    }

    @Test
    void captionShouldOmitCompleteLowerRankedItemsWhenTooLong() throws Exception {
        stubProgressReply();
        when(messenger.downloadFileBytes("large-file")).thenReturn(new byte[]{1});
        AnimeResult highest = result("最高匹配", 0.99, "https://media.trace.moe/highest.mp4");
        AnimeResult lower = result("次高匹配" + "长".repeat(900), 0.90,
                "https://media.trace.moe/lower.mp4");
        when(traceMoeService.search(any())).thenReturn(new SearchResponse(true, "ok", List.of(lower, highest)));
        Path preview = Files.write(Files.createTempFile("anime-preview-test-", ".mp4"), new byte[]{1});
        when(traceMoeService.downloadPreview(highest.previewUrl())).thenReturn(preview);
        when(messenger.sendReplyVideoByPath(eq(CHAT_ID), eq(CMD_MESSAGE_ID), eq(preview.toString()), anyString()))
                .thenReturn(true);
        ArgumentCaptor<String> captionCaptor = ArgumentCaptor.forClass(String.class);

        newHandler(Runnable::run).execute(context(commandMessage(photoMessage())));

        verify(messenger).sendReplyVideoByPath(eq(CHAT_ID), eq(CMD_MESSAGE_ID), eq(preview.toString()),
                captionCaptor.capture());
        String caption = captionCaptor.getValue();
        assertTrue(caption.length() <= 1024);
        assertTrue(caption.contains("最高匹配"));
        assertTrue(caption.contains("另有 1 条结果"));
        assertFalse(caption.contains(lower.previewUrl()));
        assertFalse(Files.exists(preview));
    }

    @Test
    void oversizedHighestResultShouldFallbackBeforeDownloadingPreview() {
        stubProgressReply();
        when(messenger.downloadFileBytes("large-file")).thenReturn(new byte[]{1});
        AnimeResult oversized = result("长".repeat(1100), 0.99, "https://media.trace.moe/preview.mp4");
        when(traceMoeService.search(any())).thenReturn(new SearchResponse(true, "ok", List.of(oversized)));

        newHandler(Runnable::run).execute(context(commandMessage(photoMessage())));

        verify(traceMoeService, never()).downloadPreview(anyString());
        verify(messenger, never()).sendReplyVideoByPath(any(), any(), anyString(), anyString());
        verify(messenger).editMessageText(eq(CHAT_ID), eq(PROGRESS_MESSAGE_ID),
                argThat(text -> text.contains("动漫识图结果") && text.contains("预览片段")), eq("HTML"));
    }

    private AnimeSearchCommandHandler newHandler(TaskExecutor executor) {
        return newHandler(executor, Map.of());
    }

    private AnimeSearchCommandHandler newHandler(
            TaskExecutor executor, Map<String, BangumiWorkTranslationService.Translation> translations) {
        BangumiWorkTranslationService translationService = mock(BangumiWorkTranslationService.class);
        org.mockito.Mockito.lenient().when(translationService.translateTitlesAsync(any()))
                .thenReturn(CompletableFuture.completedFuture(translations));
        return new AnimeSearchCommandHandler(messenger, traceMoeService, translationService, executor);
    }

    private void stubProgressReply() {
        when(messenger.sendReplyHtmlTextAndReturnMessageId(eq(CHAT_ID), eq(CMD_MESSAGE_ID), anyString()))
                .thenReturn(PROGRESS_MESSAGE_ID);
    }

    private void stubSuccessfulSearch() {
        stubProgressReply();
        when(messenger.downloadFileBytes("large-file")).thenReturn(new byte[]{1});
        AnimeResult result = result("测试动画", 0.95, "https://media.trace.moe/preview.mp4");
        when(traceMoeService.search(any())).thenReturn(new SearchResponse(true, "ok", List.of(result)));
    }

    private AnimeResult result(String nativeTitle, double similarity, String previewUrl) {
        return new AnimeResult(1, "Romaji", nativeTitle, "English", "第 1 集", 65,
                similarity, previewUrl, "https://media.trace.moe/image.jpg");
    }

    private BotContext context(Message message) {
        return new BotContext(null, UpdateType.MESSAGE, CHAT_ID, 999L, "/anime", message, null, null, null);
    }

    private Message commandMessage(Message replyTo) {
        Message message = new Message();
        message.setMessageId(CMD_MESSAGE_ID);
        message.setReplyToMessage(replyTo);
        return message;
    }

    private Message photoMessage() {
        PhotoSize small = new PhotoSize();
        small.setFileId("small-file");
        PhotoSize large = new PhotoSize();
        large.setFileId("large-file");
        Message replied = new Message();
        replied.setPhoto(List.of(small, large));
        return replied;
    }
}
