package com.github.anicmv.telegrambot.listener;

import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.entity.ChatImageEntity;
import com.github.anicmv.telegrambot.event.GroupMessageReceivedEvent;
import com.github.anicmv.telegrambot.listener.filter.BotMessageFilter;
import com.github.anicmv.telegrambot.listener.filter.GroupChatFilter;
import com.github.anicmv.telegrambot.listener.filter.GroupMessageFilter;
import com.github.anicmv.telegrambot.listener.filter.RecordConfigFilter;
import com.github.anicmv.telegrambot.listener.filter.StaticImageFilter;
import com.github.anicmv.telegrambot.repository.ChatImageRepository;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageRecordListenerTest {

    @Mock
    private TelegramClient telegramClient;

    @Mock
    private ChatImageRepository chatImageRepository;

    private BotProperties properties;
    private TaskExecutor executor;

    @BeforeEach
    void setUp() {
        properties = new BotProperties();
        properties.getProfile().setRecordEnabled(true);
        properties.getProfile().getRecordGroupIds().add(-100L);
        executor = Runnable::run;
    }

    @Test
    void shouldRecordStaticSticker() throws Exception {
        stubDownload(new byte[]{1, 2, 3});

        newListener().onGroupMessage(stickerEvent("static"));

        ArgumentCaptor<ChatImageEntity> captor = ArgumentCaptor.forClass(ChatImageEntity.class);
        verify(chatImageRepository).insert(captor.capture());
        ChatImageEntity entity = captor.getValue();
        assertEquals("sticker", entity.getImageType());
        assertEquals("uniq-1", entity.getFileUniqueId());
        assertEquals(-100L, entity.getChatId());
        assertEquals(999L, entity.getTelegramUserId());
        assertEquals("😺", entity.getEmoji());
        assertEquals("catset", entity.getSetName());
        assertEquals(512, entity.getWidth());
        assertEquals(512, entity.getHeight());
        assertArrayEquals(new byte[]{1, 2, 3}, entity.getImageData());
        assertEquals(3, entity.getFileSize());
    }

    @Test
    void shouldRecordPhoto() throws Exception {
        stubDownload(new byte[]{9});

        newListener().onGroupMessage(photoEvent());

        ArgumentCaptor<ChatImageEntity> captor = ArgumentCaptor.forClass(ChatImageEntity.class);
        verify(chatImageRepository).insert(captor.capture());
        ChatImageEntity entity = captor.getValue();
        assertEquals("photo", entity.getImageType());
        assertEquals("uniq-p", entity.getFileUniqueId());
        assertEquals(1280, entity.getWidth());
        assertEquals(720, entity.getHeight());
        assertNull(entity.getEmoji());
        assertArrayEquals(new byte[]{9}, entity.getImageData());
    }

    @Test
    void shouldNotRecordAnimatedSticker() {
        newListener().onGroupMessage(stickerEvent("animated"));

        verifyNoInteractions(chatImageRepository, telegramClient);
    }

    @Test
    void shouldNotRecordVideoSticker() {
        newListener().onGroupMessage(stickerEvent("video"));

        verifyNoInteractions(chatImageRepository, telegramClient);
    }

    @Test
    void shouldNotRecordTextMessage() {
        newListener().onGroupMessage(textEvent());

        verifyNoInteractions(chatImageRepository, telegramClient);
    }

    @Test
    void downloadFailureShouldNotPropagate() throws Exception {
        when(telegramClient.execute(any(GetFile.class))).thenThrow(new TelegramApiException("boom"));

        assertDoesNotThrow(() -> newListener().onGroupMessage(stickerEvent("static")));
        verifyNoInteractions(chatImageRepository);
    }

    private void stubDownload(byte[] bytes) throws Exception {
        org.telegram.telegrambots.meta.api.objects.File fileInfo = new org.telegram.telegrambots.meta.api.objects.File();
        when(telegramClient.execute(any(GetFile.class))).thenReturn(fileInfo);
        when(telegramClient.downloadFileAsStream(fileInfo)).thenReturn(new ByteArrayInputStream(bytes));
    }

    private ImageRecordListener newListener() {
        List<GroupMessageFilter> filters = List.of(new GroupChatFilter(), new BotMessageFilter(),
                new RecordConfigFilter(properties), new StaticImageFilter());
        return new ImageRecordListener(telegramClient, chatImageRepository, executor, filters);
    }

    private GroupMessageReceivedEvent textEvent() {
        return new GroupMessageReceivedEvent(-100L, "supergroup", 999L, "tester", "Test", false, false,
                "text", null, null, "hello", 42L, LocalDateTime.of(2026, 9, 3, 12, 0));
    }

    private GroupMessageReceivedEvent stickerEvent(String format) {
        GroupMessageReceivedEvent.StickerInfo sticker = new GroupMessageReceivedEvent.StickerInfo(
                "fid-1", "uniq-1", format, "😺", "catset", 512, 512);
        return new GroupMessageReceivedEvent(-100L, "supergroup", 999L, "tester", "Test", false, false,
                "sticker", sticker, null, "hello", 42L, LocalDateTime.of(2026, 9, 3, 12, 0));
    }

    private GroupMessageReceivedEvent photoEvent() {
        GroupMessageReceivedEvent.PhotoInfo photo = new GroupMessageReceivedEvent.PhotoInfo(
                "fid-p", "uniq-p", 1280, 720);
        return new GroupMessageReceivedEvent(-100L, "supergroup", 999L, "tester", "Test", false, false,
                "photo", null, photo, null, 42L, LocalDateTime.of(2026, 9, 3, 12, 0));
    }
}
