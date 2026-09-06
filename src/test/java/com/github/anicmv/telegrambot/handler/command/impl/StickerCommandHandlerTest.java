package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import com.github.anicmv.telegrambot.service.StickerPackService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StickerCommandHandlerTest {

    private static final Long CHAT_ID = -100123L;
    private static final Integer COMMAND_ID = 7;
    private static final Integer PROGRESS_ID = 99;

    @Mock
    private Messenger messenger;

    @Mock
    private StickerPackService stickerPackService;

    @Test
    void noReplyShouldHint() {
        newHandler(Runnable::run).execute(context(message(null)));

        verify(messenger).sendReplyText(eq(CHAT_ID), eq(COMMAND_ID), anyString());
        verifyNoInteractions(stickerPackService);
    }

    @Test
    void nonStickerReplyShouldHint() {
        Message replied = new Message();
        replied.setText("hello");

        newHandler(Runnable::run).execute(context(message(replied)));

        verify(messenger).sendReplyText(eq(CHAT_ID), eq(COMMAND_ID), anyString());
        verifyNoInteractions(stickerPackService);
    }

    @Test
    void stickerWithoutSetNameShouldStillPrepareAndSend() throws Exception {
        Path dir = Files.createTempDirectory("sticker-single-test-");
        Path file = Files.write(dir.resolve("sticker.png"), new byte[]{1});
        Sticker sticker = sticker(null);
        stubProgress();
        when(stickerPackService.prepareSingle(sticker))
                .thenReturn(new StickerPackService.PreparedSticker(file, ".png"));
        when(messenger.sendReplyDocumentByPath(eq(CHAT_ID), eq(COMMAND_ID), eq(file.toString()), anyString()))
                .thenReturn(true);

        newHandler(Runnable::run).execute(context(message(stickerMessage(sticker))));

        verify(stickerPackService).prepareSingle(sticker);
        verify(messenger).sendReplyDocumentByPath(eq(CHAT_ID), eq(COMMAND_ID), eq(file.toString()), anyString());
        verify(messenger).deleteMessageSilently(CHAT_ID, PROGRESS_ID);
        assertFalse(Files.exists(dir));
    }

    @Test
    void serviceFailureShouldEditProgressToError() throws Exception {
        Sticker sticker = sticker("file-id");
        stubProgress();
        when(stickerPackService.prepareSingle(sticker))
                .thenThrow(new IllegalStateException("贴纸下载或转换失败"));

        newHandler(Runnable::run).execute(context(message(stickerMessage(sticker))));

        verify(messenger).editMessageText(eq(CHAT_ID), eq(PROGRESS_ID),
                org.mockito.ArgumentMatchers.argThat(text -> text.contains("贴纸下载或转换失败")), eq("HTML"));
        verify(messenger, never()).sendReplyDocumentByPath(any(), any(), anyString(), anyString());
    }

    @Test
    void rejectedExecutorShouldFallback() throws Exception {
        stubProgress();
        TaskExecutor rejecting = task -> {
            throw new RejectedExecutionException("pool full");
        };

        newHandler(rejecting).execute(context(message(stickerMessage(sticker("file-id")))));

        verify(messenger).editMessageText(eq(CHAT_ID), eq(PROGRESS_ID),
                org.mockito.ArgumentMatchers.argThat(text -> text.contains("系统繁忙")), eq("HTML"));
        verifyNoInteractions(stickerPackService);
    }

    @Test
    void messageWithoutIdShouldSendDocumentDirectly() throws Exception {
        Path dir = Files.createTempDirectory("sticker-single-test-");
        Path file = Files.write(dir.resolve("sticker.png"), new byte[]{1});
        Sticker sticker = sticker("file-id");
        when(messenger.sendHtmlTextAndReturnMessageId(eq(CHAT_ID), anyString())).thenReturn(PROGRESS_ID);
        when(stickerPackService.prepareSingle(sticker))
                .thenReturn(new StickerPackService.PreparedSticker(file, ".png"));
        when(messenger.sendDocumentByPath(eq(CHAT_ID), eq(file.toString()), anyString())).thenReturn(true);
        Message command = message(stickerMessage(sticker));
        command.setMessageId(null);

        newHandler(Runnable::run).execute(context(command));

        verify(messenger).sendDocumentByPath(eq(CHAT_ID), eq(file.toString()), anyString());
        assertFalse(Files.exists(dir));
    }

    private StickerCommandHandler newHandler(TaskExecutor executor) {
        return new StickerCommandHandler(messenger, stickerPackService, executor);
    }

    private void stubProgress() {
        when(messenger.sendReplyHtmlTextAndReturnMessageId(eq(CHAT_ID), eq(COMMAND_ID), anyString()))
                .thenReturn(PROGRESS_ID);
    }

    private BotContext context(Message message) {
        return new BotContext(null, UpdateType.MESSAGE, CHAT_ID, 999L,
                "/sticker", message, null, null, null);
    }

    private Message message(Message replyTo) {
        Message message = new Message();
        message.setMessageId(COMMAND_ID);
        message.setReplyToMessage(replyTo);
        return message;
    }

    private Message stickerMessage(Sticker sticker) {
        Message replied = new Message();
        replied.setSticker(sticker);
        return replied;
    }

    private Sticker sticker(String setName) {
        Sticker sticker = new Sticker();
        sticker.setFileId("file-id");
        sticker.setSetName(setName);
        return sticker;
    }
}
