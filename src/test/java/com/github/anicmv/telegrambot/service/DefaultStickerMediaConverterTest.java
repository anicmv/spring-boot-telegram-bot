package com.github.anicmv.telegrambot.service;

import com.github.anicmv.telegrambot.config.BotProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultStickerMediaConverterTest {

    private static final byte[] WEBP_1X1 = java.util.Base64.getDecoder().decode(
            "UklGRiIAAABXRUJQVlA4IBgAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA==");

    @Test
    void shouldConvertStaticWebpToPng() throws Exception {
        Path dir = Files.createTempDirectory("converter-test-");
        try {
            Path source = Files.write(dir.resolve("source.webp"), WEBP_1X1);
            DefaultStickerMediaConverter converter = new DefaultStickerMediaConverter(
                    (command, workDirectory, timeout) -> new CommandExecutor.CommandResult(0, ""),
                    new BotProperties());

            StickerMediaConverter.ConversionResult result = converter.convert(sticker(false, false), source, dir);

            assertEquals(".png", result.extension());
            assertTrue(Files.isRegularFile(result.file()));
            assertTrue(Files.size(result.file()) > 0);
            assertEquals((byte) 0x89, Files.readAllBytes(result.file())[0]);
            assertEquals((byte) 'P', Files.readAllBytes(result.file())[1]);
        } finally {
            com.github.anicmv.telegrambot.utils.BotUtil.deleteDirectoryQuietly(dir);
        }
    }

    @Test
    void shouldRunLottieCommandForAnimatedSticker() throws Exception {
        Path dir = Files.createTempDirectory("converter-test-");
        try {
            Path source = Files.write(dir.resolve("source.tgs"), new byte[]{1});
            List<String> command = new ArrayList<>();
            DefaultStickerMediaConverter converter = new DefaultStickerMediaConverter(
                    (tokens, workDirectory, timeout) -> {
                        command.addAll(tokens);
                        Files.write(workDirectory.resolve("converted.gif"), new byte[]{1});
                        return new CommandExecutor.CommandResult(0, "");
                    }, new BotProperties());

            StickerMediaConverter.ConversionResult result = converter.convert(sticker(true, false), source, dir);

            assertEquals(".gif", result.extension());
            assertEquals("bash", command.getFirst());
            Path bundledScript = Path.of(command.get(1));
            assertEquals("lottie_to_gif.sh", bundledScript.getFileName().toString());
            assertTrue(Files.isRegularFile(bundledScript));
            assertTrue(command.contains("--output"));
            assertTrue(command.contains(source.toString()));
        } finally {
            com.github.anicmv.telegrambot.utils.BotUtil.deleteDirectoryQuietly(dir);
        }
    }

    @Test
    void shouldRunFfmpegForVideoSticker() throws Exception {
        Path dir = Files.createTempDirectory("converter-test-");
        try {
            Path source = Files.write(dir.resolve("source.webm"), new byte[]{1});
            List<String> command = new ArrayList<>();
            DefaultStickerMediaConverter converter = new DefaultStickerMediaConverter(
                    (tokens, workDirectory, timeout) -> {
                        command.addAll(tokens);
                        Files.write(workDirectory.resolve("converted.gif"), new byte[]{1});
                        return new CommandExecutor.CommandResult(0, "");
                    }, new BotProperties());

            StickerMediaConverter.ConversionResult result = converter.convert(sticker(false, true), source, dir);

            assertEquals(".gif", result.extension());
            assertEquals("ffmpeg", command.getFirst());
            assertTrue(command.contains("-i"));
            assertTrue(command.contains(source.toString()));
        } finally {
            com.github.anicmv.telegrambot.utils.BotUtil.deleteDirectoryQuietly(dir);
        }
    }

    @Test
    void failedCommandMustNotProduceConversionResult() throws Exception {
        Path dir = Files.createTempDirectory("converter-test-");
        try {
            Path source = Files.write(dir.resolve("source.webm"), new byte[]{1});
            DefaultStickerMediaConverter converter = new DefaultStickerMediaConverter(
                    (command, workDirectory, timeout) -> new CommandExecutor.CommandResult(1, "failed"),
                    new BotProperties());

            org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                    () -> converter.convert(sticker(false, true), source, dir));
        } finally {
            com.github.anicmv.telegrambot.utils.BotUtil.deleteDirectoryQuietly(dir);
        }
    }

    private Sticker sticker(boolean animated, boolean video) {
        Sticker sticker = new Sticker();
        sticker.setIsAnimated(animated);
        sticker.setIsVideo(video);
        return sticker;
    }
}
