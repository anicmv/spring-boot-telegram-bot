package com.github.anicmv.telegrambot.service;

import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;

import java.nio.file.Path;

/**
 * 将 Telegram 贴纸转换为 ZIP 所需的通用格式。
 */
@FunctionalInterface
public interface StickerMediaConverter {

    ConversionResult convert(Sticker sticker, Path source, Path workDirectory) throws Exception;

    record ConversionResult(Path file, String extension) {
    }
}
