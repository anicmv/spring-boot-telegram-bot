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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackCommandHandlerTest {

    private static final Long CHAT_ID = -100123L;
    private static final Integer CMD_MESSAGE_ID = 7;
    private static final Integer PROGRESS_MESSAGE_ID = 99;

    @Mock
    private Messenger messenger;

    @Mock
    private StickerPackService stickerPackService;

    @Test
    void noReplyShouldHint() {
        newHandler(Runnable::run).execute(context(message(null)));

        verify(messenger).sendReplyText(eq(CHAT_ID), eq(CMD_MESSAGE_ID), anyString());
        verifyNoInteractions(stickerPackService);
    }

    @Test
    void replyNonStickerShouldHint() {
        Message replied = new Message();
        replied.setText("hello");

        newHandler(Runnable::run).execute(context(message(replied)));

        verify(messenger).sendReplyText(eq(CHAT_ID), eq(CMD_MESSAGE_ID), anyString());
        verifyNoInteractions(stickerPackService);
    }

    @Test
    void stickerWithoutSetNameShouldHint() {
        newHandler(Runnable::run).execute(context(message(stickerMessage(null))));

        verify(messenger).sendReplyText(eq(CHAT_ID), eq(CMD_MESSAGE_ID), anyString());
        verifyNoInteractions(stickerPackService);
    }

    @Test
    void shouldPackAndSendDocumentOnSuccess() throws Exception {
        Path dir = Files.createTempDirectory("sticker-pack-test-");
        Path zipPath = dir.resolve("set_a.zip");
        Files.write(zipPath, new byte[]{1});
        stubProgressReply();
        when(stickerPackService.pack(eq("set_a"), any()))
                .thenReturn(new StickerPackService.PackedStickerSet("set_a", "My Pack", 3, 3, 0, false, zipPath));
        when(messenger.sendReplyDocumentByPath(eq(CHAT_ID), eq(CMD_MESSAGE_ID), anyString(), anyString()))
                .thenReturn(true);

        newHandler(Runnable::run).execute(context(message(stickerMessage("set_a"))));

        verify(messenger).sendReplyDocumentByPath(eq(CHAT_ID), eq(CMD_MESSAGE_ID), eq(zipPath.toString()),
                argThat(caption -> caption.contains("My Pack") && caption.contains("3/3")));
        verify(messenger).deleteMessageSilently(CHAT_ID, PROGRESS_MESSAGE_ID);
        assertFalse(Files.exists(dir));
    }

    @Test
    void partialFailureCaptionShouldShowSkipped() throws Exception {
        stubProgressReply();
        when(stickerPackService.pack(eq("set_a"), any())).thenReturn(new StickerPackService.PackedStickerSet(
                "set_a", "My Pack", 3, 1, 2, false, tempZip()));

        newHandler(Runnable::run).execute(context(message(stickerMessage("set_a"))));

        verify(messenger).sendReplyDocumentByPath(eq(CHAT_ID), eq(CMD_MESSAGE_ID), anyString(),
                argThat(caption -> caption.contains("2 张下载失败")));
    }

    @Test
    void truncatedCaptionShouldWarn() throws Exception {
        stubProgressReply();
        when(stickerPackService.pack(eq("set_a"), any())).thenReturn(new StickerPackService.PackedStickerSet(
                "set_a", "My Pack", 120, 90, 0, true, tempZip()));

        newHandler(Runnable::run).execute(context(message(stickerMessage("set_a"))));

        verify(messenger).sendReplyDocumentByPath(eq(CHAT_ID), eq(CMD_MESSAGE_ID), anyString(),
                argThat(caption -> caption.contains("已截断")));
    }

    @Test
    void serviceFailureShouldEditProgressToError() throws Exception {
        stubProgressReply();
        when(stickerPackService.pack(eq("set_a"), any()))
                .thenThrow(new IllegalStateException("贴纸包不存在或为空"));

        newHandler(Runnable::run).execute(context(message(stickerMessage("set_a"))));

        verify(messenger).editMessageText(eq(CHAT_ID), eq(PROGRESS_MESSAGE_ID),
                argThat(text -> text.contains("贴纸包不存在或为空")), eq("HTML"));
        verify(messenger, never()).sendReplyDocumentByPath(any(), any(), anyString(), anyString());
    }

    @Test
    void rejectedExecutorShouldFallback() throws Exception {
        stubProgressReply();
        TaskExecutor rejecting = task -> {
            throw new RejectedExecutionException("pool full");
        };

        newHandler(rejecting).execute(context(message(stickerMessage("set_a"))));

        verify(messenger).editMessageText(eq(CHAT_ID), eq(PROGRESS_MESSAGE_ID),
                argThat(text -> text.contains("系统繁忙")), eq("HTML"));
        verifyNoInteractions(stickerPackService);
    }

    @Test
    void messageWithoutIdShouldFallbackToPlainSend() throws Exception {
        when(messenger.sendHtmlTextAndReturnMessageId(eq(CHAT_ID), anyString())).thenReturn(PROGRESS_MESSAGE_ID);
        when(stickerPackService.pack(eq("set_a"), any())).thenReturn(new StickerPackService.PackedStickerSet(
                "set_a", "My Pack", 1, 1, 0, false, tempZip()));
        Message commandMessage = message(stickerMessage("set_a"));
        commandMessage.setMessageId(null);

        newHandler(Runnable::run).execute(context(commandMessage));

        verify(messenger).sendDocumentByPath(eq(CHAT_ID), anyString(), anyString());
    }

    private PackCommandHandler newHandler(TaskExecutor executor) {
        return new PackCommandHandler(messenger, stickerPackService, executor);
    }

    private void stubProgressReply() throws Exception {
        when(messenger.sendReplyHtmlTextAndReturnMessageId(eq(CHAT_ID), eq(CMD_MESSAGE_ID), anyString()))
                .thenReturn(PROGRESS_MESSAGE_ID);
    }

    private Path tempZip() throws Exception {
        Path dir = Files.createTempDirectory("sticker-pack-test-");
        return Files.write(dir.resolve("set_a.zip"), new byte[]{1});
    }

    private BotContext context(Message message) {
        return new BotContext(null, UpdateType.MESSAGE, CHAT_ID, 999L, "/pack", message, null, null, null);
    }

    private Message message(Message replyTo) {
        Message message = new Message();
        message.setMessageId(CMD_MESSAGE_ID);
        message.setReplyToMessage(replyTo);
        return message;
    }

    private Message stickerMessage(String setName) {
        Message replied = new Message();
        Sticker sticker = new Sticker();
        sticker.setSetName(setName);
        replied.setSticker(sticker);
        return replied;
    }
}
