package com.github.anicmv.telegrambot.service;

import com.github.anicmv.telegrambot.config.BotProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.stickers.GetStickerSet;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;
import org.telegram.telegrambots.meta.api.objects.stickers.StickerSet;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StickerPackServiceTest {

    @Mock
    private TelegramClient telegramClient;

    private BotProperties properties;

    @BeforeEach
    void setUp() {
        properties = new BotProperties();
    }

    @Test
    void shouldPackAllFormatsWithOrderedEntries() throws Exception {
        stubSet(sticker("f1", false, false), sticker("f2", true, false), sticker("f3", false, true));
        stubDownloads(bytes(1), bytes(2), bytes(3));

        StickerPackService.PackedStickerSet result = newService().pack("test_set", null);

        assertEquals("test_set", result.name());
        assertEquals("Test Set", result.title());
        assertEquals(3, result.totalCount());
        assertEquals(3, result.packedCount());
        assertEquals(0, result.skippedCount());
        assertFalse(result.truncated());
        try (ZipFile zip = new ZipFile(result.zipPath().toFile())) {
            assertEquals(3, zip.size());
            assertEntry(zip, "001.webp", bytes(1));
            assertEntry(zip, "002.tgs", bytes(2));
            assertEntry(zip, "003.webm", bytes(3));
        }
    }

    @Test
    void shouldSkipFailedStickerAndKeepSequentialNames() throws Exception {
        stubSet(sticker("f1", false, false), sticker("f2", false, false), sticker("f3", false, false));
        org.telegram.telegrambots.meta.api.objects.File file = metaFile();
        when(telegramClient.execute(any(GetFile.class)))
                .thenReturn(file)
                .thenThrow(new TelegramApiException("boom"))
                .thenReturn(file);
        when(telegramClient.downloadFileAsStream(any(org.telegram.telegrambots.meta.api.objects.File.class)))
                .thenAnswer(inv -> new ByteArrayInputStream(bytes(1)))
                .thenAnswer(inv -> new ByteArrayInputStream(bytes(3)));

        StickerPackService.PackedStickerSet result = newService().pack("test_set", null);

        assertEquals(3, result.totalCount());
        assertEquals(2, result.packedCount());
        assertEquals(1, result.skippedCount());
        try (ZipFile zip = new ZipFile(result.zipPath().toFile())) {
            assertEquals(2, zip.size());
            assertEntry(zip, "001.webp", bytes(1));
            assertEntry(zip, "002.webp", bytes(3));
        }
    }

    @Test
    void allDownloadsFailedShouldThrowAndCleanup() throws Exception {
        stubSet(sticker("f1", false, false), sticker("f2", false, false));
        when(telegramClient.execute(any(GetFile.class))).thenThrow(new TelegramApiException("boom"));

        List<Path> before = listPackDirs();
        assertThrows(IllegalStateException.class, () -> newService().pack("test_set", null));
        assertEquals(before, listPackDirs());
    }

    @Test
    void emptyStickersShouldThrowAndCleanup() throws Exception {
        StickerSet set = new StickerSet();
        set.setName("test_set");
        set.setTitle("Test Set");
        set.setStickers(List.of());
        when(telegramClient.execute(any(GetStickerSet.class))).thenReturn(set);

        List<Path> before = listPackDirs();
        assertThrows(IllegalStateException.class, () -> newService().pack("test_set", null));
        assertEquals(before, listPackDirs());
    }

    @Test
    void setNotFoundShouldThrow() throws Exception {
        when(telegramClient.execute(any(GetStickerSet.class))).thenThrow(new TelegramApiException("not found"));

        assertThrows(IllegalStateException.class, () -> newService().pack("test_set", null));
    }

    @Test
    void shouldTruncateWhenZipExceedsMaxBytes() throws Exception {
        properties.getPack().setMaxZipBytes(1);
        stubSet(sticker("f1", false, false), sticker("f2", false, false));
        stubDownloads(bytes(1, 2, 3), bytes(4));

        StickerPackService.PackedStickerSet result = newService().pack("test_set", null);

        assertTrue(result.truncated());
        assertEquals(1, result.packedCount());
        assertEquals(2, result.totalCount());
        try (ZipFile zip = new ZipFile(result.zipPath().toFile())) {
            assertEquals(1, zip.size());
        }
    }

    @Test
    void extensionOfShouldMapFormats() {
        assertEquals(".webp", StickerPackService.extensionOf(sticker("f", false, false)));
        assertEquals(".tgs", StickerPackService.extensionOf(sticker("f", true, false)));
        assertEquals(".webm", StickerPackService.extensionOf(sticker("f", false, true)));
    }

    private StickerPackService newService() {
        return new StickerPackService(telegramClient, properties);
    }

    private Sticker sticker(String fileId, boolean animated, boolean video) {
        Sticker sticker = new Sticker();
        sticker.setFileId(fileId);
        sticker.setIsAnimated(animated);
        sticker.setIsVideo(video);
        return sticker;
    }

    private void stubSet(Sticker... stickers) throws TelegramApiException {
        StickerSet set = new StickerSet();
        set.setName("test_set");
        set.setTitle("Test Set");
        set.setStickers(List.of(stickers));
        when(telegramClient.execute(any(GetStickerSet.class))).thenReturn(set);
    }

    private void stubDownloads(byte[]... payloads) throws Exception {
        when(telegramClient.execute(any(GetFile.class))).thenReturn(metaFile());
        Deque<byte[]> queue = new ArrayDeque<>(List.of(payloads));
        when(telegramClient.downloadFileAsStream(any(org.telegram.telegrambots.meta.api.objects.File.class)))
                .thenAnswer(inv -> new ByteArrayInputStream(queue.poll()));
    }

    private org.telegram.telegrambots.meta.api.objects.File metaFile() {
        org.telegram.telegrambots.meta.api.objects.File file = new org.telegram.telegrambots.meta.api.objects.File();
        file.setFilePath("path/x");
        return file;
    }

    private byte[] bytes(int... values) {
        byte[] data = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            data[i] = (byte) values[i];
        }
        return data;
    }

    private void assertEntry(ZipFile zip, String name, byte[] expected) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        assertTrue(entry != null, "缺少 entry: " + name);
        try (var in = zip.getInputStream(entry)) {
            assertArrayEquals(expected, in.readAllBytes());
        }
    }

    private List<Path> listPackDirs() throws IOException {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> paths = Files.list(tmp)) {
            return paths.filter(p -> p.getFileName().toString().startsWith("sticker-pack-")).sorted().toList();
        }
    }
}
