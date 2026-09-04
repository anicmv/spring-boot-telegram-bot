package com.github.anicmv.telegrambot.service;

import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.utils.BotUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.stickers.GetStickerSet;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;
import org.telegram.telegrambots.meta.api.objects.stickers.StickerSet;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * @author anicmv
 * @date 2026/9/5
 * @description 贴纸包打包服务：拉取整个贴纸包，按原格式（webp/tgs/webm）逐张下载写入 zip。
 * 纯同步服务；异步编排、占位消息与发送由调用方负责。单张下载失败跳过计数，
 * zip 超过体积上限截断；失败路径自清临时目录，成功才把 zipPath 交给调用方清理。
 */
@Log4j2
@Service
public class StickerPackService {

    private final TelegramClient telegramClient;
    private final BotProperties properties;

    public StickerPackService(TelegramClient telegramClient, BotProperties properties) {
        this.telegramClient = telegramClient;
        this.properties = properties;
    }

    /**
     * 打包结果。totalCount 为实际尝试的贴纸数（受 maxStickers 限制），
     * packedCount 为成功入包数，skippedCount 为下载失败跳过数，truncated 为体积截断标记。
     */
    public record PackedStickerSet(String name, String title, int totalCount, int packedCount,
                                   int skippedCount, boolean truncated, Path zipPath) {
    }

    /**
     * 打包指定贴纸包。progressCallback 每处理一张回调已处理数（可为 null）。
     *
     * @throws IllegalStateException 贴纸包不存在/为空、全部下载失败、IO 失败
     */
    public PackedStickerSet pack(String setName, IntConsumer progressCallback) {
        StickerSet stickerSet = fetchStickerSet(setName);
        List<Sticker> stickers = stickerSet.getStickers();
        BotProperties.Pack packProps = properties.getPack();
        List<Sticker> targets = stickers.size() > packProps.getMaxStickers()
                ? stickers.subList(0, packProps.getMaxStickers()) : stickers;

        Path dir = null;
        try {
            dir = Files.createTempDirectory("sticker-pack-");
            Path zipPath = dir.resolve(setName + ".zip");
            boolean truncated = false;
            int packed = 0;
            int skipped = 0;
            int seq = 0;
            CountingOutputStream counting = new CountingOutputStream(Files.newOutputStream(zipPath));
            try (ZipOutputStream zip = new ZipOutputStream(counting)) {
                for (int i = 0; i < targets.size(); i++) {
                    Sticker sticker = targets.get(i);
                    byte[] data;
                    try {
                        data = downloadStickerBytes(sticker);
                    } catch (Exception e) {
                        skipped++;
                        log.warn("贴纸下载失败，跳过: setName={}, fileId={}", setName, sticker.getFileId(), e);
                        continue;
                    }
                    seq++;
                    zip.putNextEntry(new ZipEntry(String.format("%03d%s", seq, extensionOf(sticker))));
                    zip.write(data);
                    zip.closeEntry();
                    packed++;
                    if (progressCallback != null) {
                        progressCallback.accept(i + 1);
                    }
                    if (counting.count() > packProps.getMaxZipBytes()) {
                        truncated = true;
                        log.warn("zip 超过体积上限，截断: setName={}, bytes={}", setName, counting.count());
                        break;
                    }
                }
            }
            if (packed == 0) {
                throw new IllegalStateException("贴纸全部下载失败");
            }
            PackedStickerSet result = new PackedStickerSet(stickerSet.getName(), stickerSet.getTitle(),
                    targets.size(), packed, skipped, truncated, zipPath);
            dir = null; // 成功：临时目录清理职责移交给调用方
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("打包失败: " + e.getMessage(), e);
        } finally {
            BotUtil.deleteDirectoryQuietly(dir);
        }
    }

    private StickerSet fetchStickerSet(String setName) {
        try {
            StickerSet stickerSet = telegramClient.execute(GetStickerSet.builder().name(setName).build());
            if (stickerSet == null || stickerSet.getStickers() == null || stickerSet.getStickers().isEmpty()) {
                throw new IllegalStateException("贴纸包不存在或为空");
            }
            return stickerSet;
        } catch (TelegramApiException e) {
            throw new IllegalStateException("获取贴纸包信息失败", e);
        }
    }

    private byte[] downloadStickerBytes(Sticker sticker) throws TelegramApiException, IOException {
        org.telegram.telegrambots.meta.api.objects.File file =
                telegramClient.execute(new GetFile(sticker.getFileId()));
        try (InputStream in = telegramClient.downloadFileAsStream(file)) {
            return in.readAllBytes();
        }
    }

    static String extensionOf(Sticker sticker) {
        if (Boolean.TRUE.equals(sticker.getIsAnimated())) {
            return ".tgs";
        }
        if (Boolean.TRUE.equals(sticker.getIsVideo())) {
            return ".webm";
        }
        return ".webp";
    }

    /**
     * 累加写出字节数的计数流，用于 zip 体积守卫。
     */
    private static final class CountingOutputStream extends OutputStream {

        private final OutputStream delegate;
        private long count;

        private CountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            count += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private long count() {
            return count;
        }
    }
}
