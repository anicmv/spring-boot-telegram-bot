package com.github.anicmv.telegrambot.service;

import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.utils.BotUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
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
 * @description 贴纸包打包服务：下载贴纸并转换为 png/gif 后逐张写入 zip。
 * 纯同步服务；异步编排、占位消息与发送由调用方负责。单张下载或转换失败跳过计数，
 * zip 超过体积上限截断；失败路径自清临时目录，成功才把 zipPath 交给调用方清理。
 */
@Log4j2
@Service
public class StickerPackService {

    private final TelegramClient telegramClient;
    private final BotProperties properties;
    private final StickerMediaConverter mediaConverter;

    @Autowired
    public StickerPackService(TelegramClient telegramClient, BotProperties properties,
                              StickerMediaConverter mediaConverter) {
        this.telegramClient = telegramClient;
        this.properties = properties;
        this.mediaConverter = mediaConverter;
    }

    /**
     * 打包结果。totalCount 为实际尝试的贴纸数（受 maxStickers 限制），
     * packedCount 为成功入包数，skippedCount 为下载或转换失败数，truncated 为体积截断标记。
     */
    public record PackedStickerSet(String name, String title, int totalCount, int packedCount,
                                   int skippedCount, boolean truncated, Path zipPath) {
    }

    /**
     * 单张贴纸转换结果。成功返回后，临时目录的清理职责由调用方负责。
     */
    public record PreparedSticker(Path file, String extension) {
    }

    /**
     * 下载并转换单张贴纸，不会读取贴纸包信息。
     *
     * @throws IllegalStateException 贴纸无效、下载失败或转换失败
     */
    public PreparedSticker prepareSingle(Sticker sticker) {
        if (sticker == null || sticker.getFileId() == null || sticker.getFileId().isBlank()) {
            throw new IllegalStateException("贴纸文件不存在");
        }

        Path dir = null;
        try {
            dir = Files.createTempDirectory("sticker-single-");
            Path source = dir.resolve("source" + extensionOf(sticker));
            byte[] sourceBytes = downloadStickerBytes(sticker);
            if (sourceBytes.length == 0) {
                throw new IOException("Telegram 文件为空");
            }
            Files.write(source, sourceBytes);

            StickerMediaConverter.ConversionResult converted = mediaConverter.convert(sticker, source, dir);
            if (converted == null || converted.file() == null || converted.extension() == null
                    || converted.extension().isBlank() || !Files.isRegularFile(converted.file())
                    || Files.size(converted.file()) == 0) {
                throw new IOException("贴纸转换未生成有效文件");
            }
            String extension = converted.extension().startsWith(".")
                    ? converted.extension() : "." + converted.extension();
            Path output = dir.resolve("sticker" + extension);
            Files.copy(converted.file(), output, StandardCopyOption.REPLACE_EXISTING);
            dir = null;
            return new PreparedSticker(output, extension);
        } catch (TelegramApiException | IOException e) {
            throw new IllegalStateException("贴纸下载或转换失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("贴纸下载或转换失败", e);
        } finally {
            BotUtil.deleteDirectoryQuietly(dir);
        }
    }

    /**
     * 打包指定贴纸包。progressCallback 每处理一张回调已处理数（可为 null）。
     *
     * @throws IllegalStateException 贴纸包不存在/为空、全部处理失败、IO 失败
     */
    public PackedStickerSet pack(String setName, IntConsumer progressCallback) {
        StickerSet stickerSet = fetchStickerSet(setName);
        List<Sticker> stickers = stickerSet.getStickers();
        BotProperties.Pack packProps = properties.getPack();
        int maxStickers = Math.max(0, packProps.getMaxStickers());
        List<Sticker> targets = stickers.size() > maxStickers
                ? stickers.subList(0, maxStickers) : stickers;

        Path dir = null;
        try {
            dir = Files.createTempDirectory("sticker-pack-");
            Path zipPath = dir.resolve(setName + ".zip");
            boolean truncated = false;
            int skipped = 0;
            List<PackEntry> entries = new ArrayList<>();

            for (int i = 0; i < targets.size(); i++) {
                Sticker sticker = targets.get(i);
                try {
                    byte[] sourceBytes = downloadStickerBytes(sticker);
                    Path source = dir.resolve(String.format("source-%03d%s", i + 1, extensionOf(sticker)));
                    Files.write(source, sourceBytes);
                    StickerMediaConverter.ConversionResult converted =
                            mediaConverter.convert(sticker, source, dir);
                    Path convertedFile = dir.resolve(String.format("converted-%03d%s", i + 1,
                            converted.extension()));
                    Files.copy(converted.file(), convertedFile, StandardCopyOption.REPLACE_EXISTING);

                    PackEntry candidate = new PackEntry(
                            String.format("%03d%s", entries.size() + 1, converted.extension()), convertedFile);
                    List<PackEntry> candidateEntries = new ArrayList<>(entries);
                    candidateEntries.add(candidate);
                    writeZip(candidateEntries, zipPath);
                    if (Files.size(zipPath) > packProps.getMaxZipBytes()) {
                        writeZip(entries, zipPath);
                        truncated = true;
                        log.warn("zip 达到体积上限，截断: setName={}, bytes={}", setName, Files.size(zipPath));
                        break;
                    }
                    entries.add(candidate);
                } catch (Exception e) {
                    skipped++;
                    log.warn("贴纸处理失败，跳过: setName={}, fileId={}", setName, sticker.getFileId(), e);
                } finally {
                    if (progressCallback != null) {
                        progressCallback.accept(i + 1);
                    }
                }
            }

            int packed = entries.size();
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
        if (file == null) {
            throw new IOException("Telegram 文件不存在");
        }
        InputStream stream = telegramClient.downloadFileAsStream(file);
        if (stream == null) {
            throw new IOException("Telegram 文件流为空");
        }
        try (InputStream in = stream) {
            return in.readAllBytes();
        }
    }

    private void writeZip(List<PackEntry> entries, Path zipPath) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (PackEntry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                Files.copy(entry.file(), zip);
                zip.closeEntry();
            }
        }
    }

    private record PackEntry(String name, Path file) {
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
}
