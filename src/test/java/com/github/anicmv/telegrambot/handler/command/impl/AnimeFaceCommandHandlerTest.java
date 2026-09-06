package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import com.github.anicmv.telegrambot.service.AnimeFaceService;
import com.github.anicmv.telegrambot.service.AnimeFaceService.Candidate;
import com.github.anicmv.telegrambot.service.AnimeFaceService.PersonResult;
import com.github.anicmv.telegrambot.service.AnimeFaceService.SearchResponse;
import com.github.anicmv.telegrambot.service.StickerImageService;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.scheduling.TaskScheduler;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnimeFaceCommandHandlerTest {

    private static final Long CHAT_ID = -100123L;
    private static final Integer COMMAND_ID = 7;
    private static final Integer PROGRESS_ID = 99;

    @Test
    void noStickerReplyShouldHint() {
        Messenger messenger = mock(Messenger.class);
        AnimeFaceService service = mock(AnimeFaceService.class);
        StickerImageService imageService = mock(StickerImageService.class);

        newHandler(messenger, service, Runnable::run).execute(context(message(null)));

        verify(messenger).sendReplyText(eq(CHAT_ID), eq(COMMAND_ID), anyString());
        verifyNoInteractions(service);
    }

    @Test
    void shouldDownloadStickerAndReplyRecognition() {
        Messenger messenger = mock(Messenger.class);
        AnimeFaceService service = mock(AnimeFaceService.class);
        StickerImageService imageService = mock(StickerImageService.class);
        Sticker sticker = sticker("sticker-file");
        stubProgress(messenger);
        when(messenger.downloadFileBytes("sticker-file")).thenReturn(new byte[]{1});
        when(imageService.normalize(sticker, new byte[]{1})).thenReturn(new byte[]{2});
        SearchResponse response = new SearchResponse(true, "识别成功", "trace", false,
                List.of(new PersonResult(false, List.of(new Candidate("Clover Day's", "鷹倉杏鈴")))));
        when(imageService.normalize(sticker, new byte[]{1})).thenReturn(new byte[]{2});
        when(service.search(new byte[]{2})).thenReturn(response);
        when(service.formatHtml(response)).thenReturn("<b>识别结果</b>");

        newHandler(messenger, service, imageService, Runnable::run).execute(context(message(stickerMessage(sticker))));

        verify(service).search(new byte[]{2});
        verify(messenger).editMessageText(eq(CHAT_ID), eq(PROGRESS_ID), eq("<b>识别结果</b>"), eq("HTML"));
    }

    @Test
    void recognitionFailureShouldEditAndScheduleDelete() {
        Messenger messenger = mock(Messenger.class);
        AnimeFaceService service = mock(AnimeFaceService.class);
        StickerImageService imageService = mock(StickerImageService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        Sticker sticker = sticker("sticker-file");
        stubProgress(messenger);
        when(messenger.downloadFileBytes("sticker-file")).thenReturn(new byte[]{1});
        when(service.search(new byte[]{2})).thenThrow(new RuntimeException("offline"));

        newHandler(messenger, service, Runnable::run, scheduler)
                .execute(context(message(stickerMessage(sticker("sticker-file")))));

        verify(messenger).editMessageText(eq(CHAT_ID), eq(PROGRESS_ID),
                eq("<b>❌ 识别服务暂时不可用</b>"), eq("HTML"));
        org.mockito.ArgumentCaptor<Runnable> task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), org.mockito.ArgumentMatchers.any(Instant.class));
        task.getValue().run();
        verify(messenger).deleteMessageSilently(CHAT_ID, PROGRESS_ID);
    }
    @Test
    void rejectedExecutorShouldReplyBusy() {
        Messenger messenger = mock(Messenger.class);
        AnimeFaceService service = mock(AnimeFaceService.class);
        StickerImageService imageService = mock(StickerImageService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        stubProgress(messenger);

        newHandler(messenger, service, new TaskExecutorRejecting(), scheduler)
                .execute(context(message(stickerMessage(sticker("file")))));

        verify(messenger).editMessageText(eq(CHAT_ID), eq(PROGRESS_ID),
                org.mockito.ArgumentMatchers.argThat(text -> text.contains("任务较多")), eq("HTML"));
        verifyNoInteractions(service);
    }

    private AnimeFaceCommandHandler newHandler(Messenger messenger, AnimeFaceService service,
                                               org.springframework.core.task.TaskExecutor executor) {
        return newHandler(messenger, service, mock(StickerImageService.class), executor,
                mock(TaskScheduler.class));
    }

    private AnimeFaceCommandHandler newHandler(Messenger messenger, AnimeFaceService service,
                                               StickerImageService imageService,
                                               org.springframework.core.task.TaskExecutor executor) {
        return newHandler(messenger, service, imageService, executor, mock(TaskScheduler.class));
    }

    private AnimeFaceCommandHandler newHandler(Messenger messenger, AnimeFaceService service,
                                               org.springframework.core.task.TaskExecutor executor,
                                               TaskScheduler scheduler) {
        return newHandler(messenger, service, mock(StickerImageService.class), executor, scheduler);
    }

    private AnimeFaceCommandHandler newHandler(Messenger messenger, AnimeFaceService service,
                                               StickerImageService imageService,
                                               org.springframework.core.task.TaskExecutor executor,
                                               TaskScheduler scheduler) {
        return new AnimeFaceCommandHandler(messenger, service, imageService, executor, scheduler);
    }

    private void stubProgress(Messenger messenger) {
        when(messenger.sendReplyHtmlTextAndReturnMessageId(eq(CHAT_ID), eq(COMMAND_ID), anyString()))
                .thenReturn(PROGRESS_ID);
    }

    private BotContext context(Message message) {
        return new BotContext(null, UpdateType.MESSAGE, CHAT_ID, 999L,
                "/aniface", message, null, null, null);
    }

    private Message message(Message reply) {
        Message message = new Message();
        message.setMessageId(COMMAND_ID);
        message.setReplyToMessage(reply);
        return message;
    }

    private Message stickerMessage(Sticker sticker) {
        Message message = new Message();
        message.setSticker(sticker);
        return message;
    }

    private Sticker sticker(String fileId) {
        Sticker sticker = new Sticker();
        sticker.setFileId(fileId);
        return sticker;
    }

    private static class TaskExecutorRejecting implements org.springframework.core.task.TaskExecutor {
        @Override
        public void execute(Runnable task) {
            throw new RejectedExecutionException("busy");
        }
    }
}
