package com.github.anicmv.telegrambot.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StickerImageServicePhotoTest {

    @Test
    void shouldNormalizePhotoToJpeg() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        StickerImageService service = new StickerImageService((sticker, source, dir) -> null);

        byte[] result = service.normalizePhoto(output.toByteArray());

        assertTrue(result.length > 3);
        assertTrue((result[0] & 0xff) == 0xff && (result[1] & 0xff) == 0xd8);
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(result)));
    }
}
