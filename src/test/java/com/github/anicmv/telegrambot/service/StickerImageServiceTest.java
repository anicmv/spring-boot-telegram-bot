package com.github.anicmv.telegrambot.service;

import com.github.anicmv.telegrambot.config.BotProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StickerImageServiceTest {

    @Test
    void shouldNormalizePngLikeStaticStickerToJpeg() throws Exception {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, 0x00ff00);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(source, "png", output);
        StickerMediaConverter converter = (sticker, path, directory) ->
                new StickerMediaConverter.ConversionResult(path, ".png");
        StickerImageService service = new StickerImageService(converter);

        byte[] result = service.normalize(sticker(false, false), output.toByteArray());

        assertTrue(result.length > 3);
        assertEquals((byte) 0xff, result[0]);
        assertEquals((byte) 0xd8, result[1]);
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(result)));
    }

    @Test
    void shouldNormalizeGifFirstFrameToJpeg() throws Exception {
        Path unused = Files.createTempDirectory("aniface-test-");
        try {
            StickerMediaConverter converter = (sticker, source, directory) -> {
                Path gif = directory.resolve("converted.gif");
                BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
                ImageIO.write(image, "gif", gif.toFile());
                return new StickerMediaConverter.ConversionResult(gif, ".gif");
            };
            StickerImageService service = new StickerImageService(converter);
            byte[] result = service.normalize(sticker(true, false), new byte[]{1});

            assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(result)));
        } finally {
            com.github.anicmv.telegrambot.utils.BotUtil.deleteDirectoryQuietly(unused);
        }
    }

    @Test
    void shouldRejectEmptyBytes() {
        StickerImageService service = new StickerImageService(mock(StickerMediaConverter.class));

        assertThrows(IllegalStateException.class,
                () -> service.normalize(sticker(false, false), new byte[0]));
    }

    private Sticker sticker(boolean animated, boolean video) {
        Sticker sticker = new Sticker();
        sticker.setFileId("file");
        sticker.setIsAnimated(animated);
        sticker.setIsVideo(video);
        return sticker;
    }
}
