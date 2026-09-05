package com.github.anicmv.telegrambot.event;

import com.github.anicmv.telegrambot.model.BotContext;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 消息接收事件（私聊/群聊均发布，由监听器侧过滤），
 * 由 {@code UpdateDispatcher} 在 MESSAGE 更新路由前发布。
 * 采用纯 JDK 字段，监听器侧无需依赖 Telegram SDK；不可记录的更新（服务消息等）返回 Optional.empty()。
 */
public record MessageReceivedEvent(
        Long chatId,
        String chatType,
        Long userId,
        String username,
        String nickname,
        boolean senderIsBot,
        boolean forwarded,
        boolean viaBot,
        String messageType,
        StickerInfo sticker,
        PhotoInfo photo,
        String text,
        Long telegramMessageId,
        LocalDateTime sentAt
) {

    public static final int MAX_TEXT_LENGTH = 2000;

    /**
     * 贴纸信息；非贴纸消息为 null。format 取值：static（webp）/animated（tgs）/video（webm）。
     */
    public record StickerInfo(
            String fileId,
            String fileUniqueId,
            String format,
            String emoji,
            String setName,
            Integer width,
            Integer height
    ) {
    }

    /**
     * 图片信息（取最大尺寸），非图片消息为 null。
     */
    public record PhotoInfo(
            String fileId,
            String fileUniqueId,
            Integer width,
            Integer height
    ) {
    }

    public boolean isGroupChat() {
        return "group".equals(chatType) || "supergroup".equals(chatType);
    }

    public boolean isStaticSticker() {
        return sticker != null && "static".equals(sticker.format());
    }

    public boolean isPhotoMessage() {
        return photo != null;
    }

    public static Optional<MessageReceivedEvent> from(BotContext context) {
        if (context == null) {
            return Optional.empty();
        }
        Message message = context.message();
        if (message == null) {
            return Optional.empty();
        }
        Chat chat = message.getChat();
        if (chat == null) {
            return Optional.empty();
        }
        String messageType = resolveMessageType(message);
        if (messageType == null) {
            return Optional.empty();
        }
        User from = message.getFrom();
        return Optional.of(new MessageReceivedEvent(
                chat.getId(),
                chat.getType(),
                from != null ? from.getId() : null,
                from != null ? from.getUserName() : null,
                from != null ? displayName(from) : null,
                from != null && from.getIsBot(),
                message.getForwardOrigin() != null,
                message.getViaBot() != null,
                messageType,
                resolveStickerInfo(message.getSticker()),
                resolvePhotoInfo(message),
                truncate(resolveText(message)),
                message.getMessageId() != null ? message.getMessageId().longValue() : null,
                message.getDate() != null
                        ? LocalDateTime.ofInstant(Instant.ofEpochSecond(message.getDate()), ZoneId.systemDefault())
                        : null
        ));
    }

    private static StickerInfo resolveStickerInfo(Sticker sticker) {
        if (sticker == null) {
            return null;
        }
        String format = Boolean.TRUE.equals(sticker.getIsAnimated()) ? "animated"
                : Boolean.TRUE.equals(sticker.getIsVideo()) ? "video" : "static";
        return new StickerInfo(sticker.getFileId(), sticker.getFileUniqueId(), format,
                sticker.getEmoji(), sticker.getSetName(), sticker.getWidth(), sticker.getHeight());
    }

    private static PhotoInfo resolvePhotoInfo(Message message) {
        if (!message.hasPhoto()) {
            return null;
        }
        List<PhotoSize> sizes = message.getPhoto();
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        PhotoSize largest = sizes.getLast();
        return new PhotoInfo(largest.getFileId(), largest.getFileUniqueId(),
                largest.getWidth(), largest.getHeight());
    }

    private static String resolveMessageType(Message message) {
        if (message.getText() != null) {
            return "text";
        }
        if (message.hasPhoto()) {
            return "photo";
        }
        if (message.hasSticker()) {
            return "sticker";
        }
        if (message.hasVideo()) {
            return "video";
        }
        if (message.hasVoice()) {
            return "voice";
        }
        return null;
    }

    private static String resolveText(Message message) {
        if (message.getText() != null) {
            return message.getText();
        }
        return message.getCaption();
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH);
    }

    private static String displayName(User from) {
        String firstName = from.getFirstName().trim();
        String lastName = from.getLastName() == null ? "" : from.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? from.getUserName() : fullName;
    }
}
