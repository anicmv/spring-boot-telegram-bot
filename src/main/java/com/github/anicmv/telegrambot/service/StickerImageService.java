package com.github.anicmv.telegrambot.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;

/**
 * 将 Telegram 贴纸统一转换成 AnimTrace 可以识别的 JPEG 图片。
 * 动态贴纸和视频贴纸只取转换结果的第一帧。
 */
@Service
public class StickerImageService {

    private static final int MAX_SOURCE_BYTES = 49 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;
    private static final int MAX_OUTPUT_BYTES = 10 * 1024 * 1024;

    private final StickerMediaConverter mediaConverter;

    @Autowired
    public StickerImageService(StickerMediaConverter mediaConverter) {
        this.mediaConverter = mediaConverter;
    }

    public byte[] normalizePhoto(byte[] sourceBytes) {
        return normalizeImage(sourceBytes, null, false);
    }

    public byte[] normalize(Sticker sticker, byte[] sourceBytes) {
        if (sticker == null) {
            throw new IllegalStateException("贴纸信息为空");
        }
        return normalizeImage(sourceBytes, sticker, true);
    }

    private byte[] normalizeImage(byte[] sourceBytes, Sticker sticker, boolean convertSticker) {
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new IllegalStateException("图片内容为空");
        }
        if (sourceBytes.length > MAX_SOURCE_BYTES) {
            throw new IllegalStateException("贴纸图片超过大小限制");
        }

        Path directory = null;
        try {
            directory = Files.createTempDirectory("aniface-");
            Path source = directory.resolve(convertSticker
                    ? "source" + StickerPackService.extensionOf(sticker)
                    : "source.image");
            Files.write(source, sourceBytes);
            Path convertedFile = source;
            if (convertSticker) {
                StickerMediaConverter.ConversionResult converted = mediaConverter.convert(sticker, source, directory);
                if (converted == null || converted.file() == null || !Files.isRegularFile(converted.file())
                        || Files.size(converted.file()) == 0) {
                    throw new IOException("贴纸转换没有生成图片");
                }
                convertedFile = converted.file();
            }

            BufferedImage image = ImageIO.read(convertedFile.toFile());
            if (image == null) {
                throw new IOException("转换结果不是有效图片");
            }
            long pixels = (long) image.getWidth() * image.getHeight();
            if (pixels <= 0 || pixels > MAX_IMAGE_PIXELS) {
                throw new IOException("转换结果图片尺寸不合法");
            }
            return encodeJpeg(image);
        } catch (Exception e) {
            if (e instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException("贴纸无法转换为识别图片", e);
        } finally {
            com.github.anicmv.telegrambot.utils.BotUtil.deleteDirectoryQuietly(directory);
        }
    }

    private byte[] encodeJpeg(BufferedImage source) throws IOException {
        BufferedImage flattened = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = flattened.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, flattened.getWidth(), flattened.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(flattened, "jpeg", output)) {
            throw new IOException("JPEG 写入失败");
        }
        byte[] bytes = output.toByteArray();
        if (bytes.length == 0 || bytes.length > MAX_OUTPUT_BYTES) {
            throw new IOException("JPEG 输出超过大小限制");
        }
        return bytes;
    }
}
