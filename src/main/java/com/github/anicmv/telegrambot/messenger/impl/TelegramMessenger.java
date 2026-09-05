package com.github.anicmv.telegrambot.messenger.impl;

import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.utils.HttpUtil;
import com.github.anicmv.telegrambot.event.UpdateHandledEvent;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.InlineButton;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.methods.GetUserProfilePhotos;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.objects.chat.ChatFullInfo;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaVideo;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.UserProfilePhotos;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description Telegram SDK 适配实现，封装消息发送与回调响应。
 */
@Log4j2
public class TelegramMessenger implements Messenger {

    private static final String TELEGRAM_FILE_BASE_URL = "https://api.telegram.org/file/bot";
    /** GetUserProfilePhotos 官方单页上限 */
    private static final int MAX_AVATAR_FETCH = 100;
    /** SendMediaGroup 官方单组上限 */
    private static final int MAX_ALBUM_SIZE = 10;

    private final TelegramClient telegramClient;
    private final ApplicationEventPublisher eventPublisher;
    private final String botToken;

    public TelegramMessenger(TelegramClient telegramClient,
                              ApplicationEventPublisher eventPublisher,
                              String botToken) {
        this.telegramClient = telegramClient;
        this.eventPublisher = eventPublisher;
        this.botToken = botToken;
    }

    @Override
    public void sendText(Long chatId, String text) {
        if (chatId == null || text == null || text.isBlank()) {
            return;
        }
        SendMessage sendMessage = SendMessage.builder().chatId(chatId).text(text).build();
        try {
            telegramClient.execute(sendMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("message", chatId));
        } catch (TelegramApiException e) {
            log.error("Failed to send text message to chat {}", chatId, e);
        }
    }

    @Override
    public void sendReplyText(Long chatId, Integer replyToMessageId, String text) {
        if (chatId == null || replyToMessageId == null || text == null || text.isBlank()) {
            return;
        }
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyToMessageId(replyToMessageId)
                .build();
        try {
            telegramClient.execute(sendMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("reply_message", chatId));
        } catch (TelegramApiException e) {
            log.error("Failed to send reply message to chat {}, messageId {}", chatId, replyToMessageId, e);
        }
    }

    @Override
    public Integer sendReplyTextAndReturnMessageId(Long chatId, Integer replyToMessageId, String text) {
        if (chatId == null || replyToMessageId == null || text == null || text.isBlank()) {
            return null;
        }
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyToMessageId(replyToMessageId)
                .build();
        try {
            Message message = telegramClient.execute(sendMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("reply_message", chatId));
            return message != null ? message.getMessageId() : null;
        } catch (TelegramApiException e) {
            log.error("Failed to send reply message to chat {}, messageId {}", chatId, replyToMessageId, e);
            return null;
        }
    }

    @Override
    public void sendHtmlText(Long chatId, String text) {
        if (chatId == null || text == null || text.isBlank()) {
            return;
        }
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .build();
        try {
            telegramClient.execute(sendMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("html_message", chatId));
        } catch (TelegramApiException e) {
            log.error("Failed to send html message to chat {}", chatId, e);
        }
    }

    @Override
    public Integer sendHtmlTextAndReturnMessageId(Long chatId, String text) {
        if (chatId == null || text == null || text.isBlank()) {
            return null;
        }
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .build();
        try {
            Message message = telegramClient.execute(sendMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("html_message", chatId));
            return message != null ? message.getMessageId() : null;
        } catch (TelegramApiException e) {
            log.error("Failed to send html message to chat {}", chatId, e);
            return null;
        }
    }

    @Override
    public void sendReplyHtmlText(Long chatId, Integer replyToMessageId, String text) {
        if (chatId == null || replyToMessageId == null || text == null || text.isBlank()) {
            return;
        }
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyToMessageId(replyToMessageId)
                .build();
        try {
            telegramClient.execute(sendMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("reply_html_message", chatId));
        } catch (TelegramApiException e) {
            log.error("Failed to send reply html message to chat {}, messageId {}", chatId, replyToMessageId, e);
        }
    }

    @Override
    public Integer sendReplyHtmlTextAndReturnMessageId(Long chatId, Integer replyToMessageId, String text) {
        if (chatId == null || replyToMessageId == null || text == null || text.isBlank()) {
            return null;
        }
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyToMessageId(replyToMessageId)
                .build();
        try {
            Message message = telegramClient.execute(sendMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("reply_html_message", chatId));
            return message != null ? message.getMessageId() : null;
        } catch (TelegramApiException e) {
            log.error("Failed to send reply html message to chat {}, messageId {}", chatId, replyToMessageId, e);
            return null;
        }
    }

    @Override
    public void sendMarkdownV2Text(Long chatId, String text) {
        if (chatId == null || text == null || text.isBlank()) {
            return;
        }
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("MarkdownV2")
                .build();
        try {
            telegramClient.execute(sendMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("markdown_v2_message", chatId));
        } catch (TelegramApiException e) {
            log.error("Failed to send markdown v2 message to chat {}", chatId, e);
        }
    }

    @Override
    public void sendReplyMarkdownV2Text(Long chatId, Integer replyToMessageId, String text) {
        if (chatId == null || replyToMessageId == null || text == null || text.isBlank()) {
            return;
        }
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("MarkdownV2")
                .replyToMessageId(replyToMessageId)
                .build();
        try {
            telegramClient.execute(sendMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("reply_markdown_v2_message", chatId));
        } catch (TelegramApiException e) {
            log.error("Failed to send reply markdown v2 message to chat {}, messageId {}", chatId, replyToMessageId, e);
        }
    }

    @Override
    public Integer sendReplyMarkdownV2TextAndReturnMessageId(Long chatId, Integer replyToMessageId, String text) {
        if (chatId == null || replyToMessageId == null || text == null || text.isBlank()) {
            return null;
        }
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("MarkdownV2")
                .replyToMessageId(replyToMessageId)
                .build();
        try {
            Message message = telegramClient.execute(sendMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("reply_markdown_v2_message", chatId));
            return message != null ? message.getMessageId() : null;
        } catch (TelegramApiException e) {
            log.error("Failed to send reply markdown v2 message to chat {}, messageId {}", chatId, replyToMessageId, e);
            return null;
        }
    }

    @Override
    public void sendTextWithInlineButtons(Long chatId, String text, List<InlineButton> buttons) {
        if (chatId == null || text == null || text.isBlank() || buttons == null || buttons.isEmpty()) {
            return;
        }
        InlineKeyboardRow row = new InlineKeyboardRow(buttons.stream()
                .map(button -> InlineKeyboardButton.builder()
                        .text(button.text())
                        .callbackData(button.callbackData())
                        .build())
                .toList());
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(row));
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(markup)
                .build();
        try {
            telegramClient.execute(sendMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("message_with_inline_buttons", chatId));
        } catch (TelegramApiException e) {
            log.error("Failed to send message with inline buttons to chat {}", chatId, e);
        }
    }

    @Override
    public void sendReplyTextWithInlineButtons(Long chatId, Integer replyToMessageId, String text, List<InlineButton> buttons) {
        if (chatId == null || replyToMessageId == null || text == null || text.isBlank() || buttons == null || buttons.isEmpty()) {
            return;
        }
        InlineKeyboardRow row = new InlineKeyboardRow(buttons.stream()
                .map(button -> InlineKeyboardButton.builder()
                        .text(button.text())
                        .callbackData(button.callbackData())
                        .build())
                .toList());
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(row));
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyToMessageId(replyToMessageId)
                .replyMarkup(markup)
                .build();
        try {
            telegramClient.execute(sendMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("reply_message_with_inline_buttons", chatId));
        } catch (TelegramApiException e) {
            log.error("Failed to send reply message with inline buttons to chat {}, messageId {}", chatId, replyToMessageId, e);
        }
    }

    @Override
    public void sendTextWithSwitchInlineButton(Long chatId, String text, String buttonText, String inlineQuery) {
        if (chatId == null || text == null || text.isBlank()) {
            return;
        }
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text(buttonText == null || buttonText.isBlank() ? "Inline" : buttonText)
                .switchInlineQueryCurrentChat(inlineQuery == null ? "" : inlineQuery)
                .build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button)));
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(markup)
                .build();
        try {
            telegramClient.execute(sendMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("message_with_switch_inline_button", chatId));
        } catch (TelegramApiException e) {
            log.error("Failed to send message with switch inline button to chat {}", chatId, e);
        }
    }

    @Override
    public void sendReplyTextWithSwitchInlineButton(Long chatId, Integer replyToMessageId, String text, String buttonText, String inlineQuery) {
        if (chatId == null || replyToMessageId == null || text == null || text.isBlank()) {
            return;
        }
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text(buttonText == null || buttonText.isBlank() ? "Inline" : buttonText)
                .switchInlineQueryCurrentChat(inlineQuery == null ? "" : inlineQuery)
                .build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button)));
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyToMessageId(replyToMessageId)
                .replyMarkup(markup)
                .build();
        try {
            telegramClient.execute(sendMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("reply_message_with_switch_inline_button", chatId));
        } catch (TelegramApiException e) {
            log.error("Failed to send reply message with switch inline button to chat {}, messageId {}", chatId, replyToMessageId, e);
        }
    }

    @Override
    public void answerCallback(String callbackQueryId, String text) {
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            return;
        }
        AnswerCallbackQuery answerCallbackQuery = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(text)
                .showAlert(false)
                .build();
        try {
            telegramClient.execute(answerCallbackQuery);
            eventPublisher.publishEvent(new UpdateHandledEvent("callback", null));
        } catch (TelegramApiException e) {
            log.error("Failed to answer callback {}", callbackQueryId, e);
        }
    }

    @Override
    public void sendPhotoByUrl(Long chatId, String photoUrl, String caption) {
        sendPhotoByUrl(chatId, photoUrl, caption, null);
    }

    @Override
    public void sendPhotoByUrl(Long chatId, String photoUrl, String caption, String parseMode) {
        if (chatId == null || photoUrl == null || photoUrl.isBlank()) {
            return;
        }
        SendPhoto.SendPhotoBuilder<?, ?> builder = SendPhoto.builder()
                .chatId(chatId)
                .photo(new InputFile(photoUrl))
                .caption(caption);
        if (parseMode != null && !parseMode.isBlank()) {
            builder.parseMode(parseMode);
        }
        SendPhoto sendPhoto = builder.build();
        try {
            telegramClient.execute(sendPhoto);
            eventPublisher.publishEvent(new UpdateHandledEvent("photo", chatId));
        } catch (TelegramApiException e) {
            log.error("Failed to send photo by url to chat {}", chatId, e);
        }
    }

    @Override
    public void sendReplyPhotoByUrl(Long chatId, Integer replyToMessageId, String photoUrl, String caption) {
        if (chatId == null || replyToMessageId == null || photoUrl == null || photoUrl.isBlank()) {
            return;
        }
        SendPhoto sendPhoto = SendPhoto.builder()
                .chatId(chatId)
                .replyToMessageId(replyToMessageId)
                .photo(new InputFile(photoUrl))
                .caption(caption)
                .build();
        try {
            telegramClient.execute(sendPhoto);
            eventPublisher.publishEvent(new UpdateHandledEvent("reply_photo", chatId));
        } catch (TelegramApiException e) {
            log.error("Failed to send reply photo by url to chat {}, messageId {}", chatId, replyToMessageId, e);
        }
    }

    @Override
    public boolean sendVideoByPath(Long chatId, String videoPath, String caption) {
        if (chatId == null || videoPath == null || videoPath.isBlank()) {
            return false;
        }
        SendVideo.SendVideoBuilder<?, ?> builder = SendVideo.builder()
                .chatId(chatId)
                .video(new InputFile(new File(videoPath)))
                .caption(caption)
                .parseMode("HTML")
                .supportsStreaming(true)
                ;
        VideoSize size = probeVideoSize(videoPath);
        if (size != null) {
            builder.width(size.width());
            builder.height(size.height());
        }
        SendVideo sendVideo = builder.build();
        try {
            telegramClient.execute(sendVideo);
            eventPublisher.publishEvent(new UpdateHandledEvent("video", chatId));
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to send video by path to chat {}, path={}", chatId, videoPath, e);
            return false;
        }
    }

    @Override
    public boolean sendReplyVideoByPath(Long chatId, Integer replyToMessageId, String videoPath, String caption) {
        if (chatId == null || replyToMessageId == null || videoPath == null || videoPath.isBlank()) {
            return false;
        }
        SendVideo.SendVideoBuilder<?, ?> builder = SendVideo.builder()
                .chatId(chatId)
                .replyToMessageId(replyToMessageId)
                .video(new InputFile(new File(videoPath)))
                .caption(caption)
                .parseMode("HTML")
                .supportsStreaming(true)
                ;
        VideoSize size = probeVideoSize(videoPath);
        if (size != null) {
            builder.width(size.width());
            builder.height(size.height());
        }
        SendVideo sendVideo = builder.build();
        try {
            telegramClient.execute(sendVideo);
            eventPublisher.publishEvent(new UpdateHandledEvent("reply_video", chatId));
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to send reply video by path to chat {}, messageId {}, path={}", chatId, replyToMessageId, videoPath, e);
            return false;
        }
    }

    @Override
    public boolean sendDocumentByPath(Long chatId, String documentPath, String caption) {
        if (chatId == null || documentPath == null || documentPath.isBlank()) {
            return false;
        }
        SendDocument sendDocument = SendDocument.builder()
                .chatId(chatId)
                .document(new InputFile(new File(documentPath)))
                .caption(caption)
                .parseMode("HTML")
                .build();
        try {
            telegramClient.execute(sendDocument);
            eventPublisher.publishEvent(new UpdateHandledEvent("document", chatId));
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to send document by path to chat {}, path={}", chatId, documentPath, e);
            return false;
        }
    }

    @Override
    public boolean sendReplyDocumentByPath(Long chatId, Integer replyToMessageId, String documentPath, String caption) {
        if (chatId == null || replyToMessageId == null || documentPath == null || documentPath.isBlank()) {
            return false;
        }
        SendDocument sendDocument = SendDocument.builder()
                .chatId(chatId)
                .replyToMessageId(replyToMessageId)
                .document(new InputFile(new File(documentPath)))
                .caption(caption)
                .parseMode("HTML")
                .build();
        try {
            telegramClient.execute(sendDocument);
            eventPublisher.publishEvent(new UpdateHandledEvent("reply_document", chatId));
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to send reply document by path to chat {}, messageId {}, path={}", chatId, replyToMessageId, documentPath, e);
            return false;
        }
    }

    private VideoSize probeVideoSize(String videoPath) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "ffprobe",
                    "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=width,height",
                    "-of", "csv=p=0:s=x",
                    videoPath
            ).start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                return null;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (output.isBlank() || !output.contains("x")) {
                return null;
            }
            String[] parts = output.split("x");
            if (parts.length != 2) {
                return null;
            }
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            if (width <= 0 || height <= 0) {
                return null;
            }
            return new VideoSize(width, height);
        } catch (IOException | InterruptedException | NumberFormatException e) {
            return null;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    private record VideoSize(int width, int height) {
    }

    @Override
    public void deleteMessageSilently(Long chatId, Integer messageId) {
        if (chatId == null || messageId == null) {
            return;
        }
        DeleteMessage deleteMessage = DeleteMessage.builder()
                .chatId(chatId)
                .messageId(messageId)
                .build();
        try {
            telegramClient.execute(deleteMessage);
            eventPublisher.publishEvent(new UpdateHandledEvent("delete_message", chatId));
        } catch (TelegramApiException ignored) {
            // Ignore on purpose: delete failure should not break business flow.
        }
    }

    @Override
    public void editMessageText(Long chatId, Integer messageId, String text, String parseMode) {
        if (chatId == null || messageId == null || text == null || text.isBlank()) {
            return;
        }
        EditMessageText.EditMessageTextBuilder<?, ?> builder = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text);
        if (parseMode != null && !parseMode.isBlank()) {
            builder.parseMode(parseMode);
        }
        try {
            telegramClient.execute(builder.build());
            eventPublisher.publishEvent(new UpdateHandledEvent("edit_message_text", chatId));
        } catch (TelegramApiException e) {
            log.error("Failed to edit message text chatId={}, messageId={}", chatId, messageId, e);
        }
    }

    @Override
    public String uploadPhotoAndEchoFileId(Long channelId, String urlOrPath) {
        if (channelId == null || urlOrPath == null || urlOrPath.isBlank()) {
            return null;
        }
        SendPhoto.SendPhotoBuilder<?, ?> builder = SendPhoto.builder().chatId(channelId);
        if (urlOrPath.startsWith("http")) {
            builder.photo(new InputFile(urlOrPath));
        } else {
            builder.photo(new InputFile(new File(urlOrPath)));
        }
        try {
            Message message = telegramClient.execute(builder.build());
            if (message == null || message.getPhoto() == null || message.getPhoto().isEmpty()) {
                return null;
            }
            String fileId = message.getPhoto().stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .map(PhotoSize::getFileId)
                    .orElse(null);
            if (fileId != null) {
                telegramClient.execute(SendMessage.builder().chatId(channelId).text(fileId).build());
            }
            eventPublisher.publishEvent(new UpdateHandledEvent("upload_photo_and_file_id", channelId));
            return fileId;
        } catch (TelegramApiException e) {
            log.error("Failed to upload photo and echo file id to channel {}", channelId, e);
            return null;
        }
    }

    @Override
    public String uploadPhotoBytes(Long chatId, byte[] data) {
        if (chatId == null || data == null || data.length == 0) {
            return null;
        }
        SendPhoto sendPhoto = SendPhoto.builder()
                .chatId(chatId)
                .photo(new InputFile(new ByteArrayInputStream(data), "avatar.jpg"))
                .build();
        try {
            Message message = telegramClient.execute(sendPhoto);
            if (message == null || message.getPhoto() == null || message.getPhoto().isEmpty()) {
                return null;
            }
            String fileId = message.getPhoto().stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .map(PhotoSize::getFileId)
                    .orElse(null);
            deleteMessageSilently(chatId, message.getMessageId());
            eventPublisher.publishEvent(new UpdateHandledEvent("upload_photo_bytes", chatId));
            return fileId;
        } catch (TelegramApiException e) {
            log.error("Failed to upload photo bytes to chat {}", chatId, e);
            return null;
        }
    }

    @Override
    public String getUserAvatarFileId(Long telegramUserId) {
        if (telegramUserId == null) {
            return null;
        }
        GetUserProfilePhotos request = GetUserProfilePhotos.builder()
                .userId(telegramUserId)
                .offset(0)
                .limit(1)
                .build();
        try {
            UserProfilePhotos photos = telegramClient.execute(request);
            if (photos == null || photos.getPhotos() == null || photos.getPhotos().isEmpty()) {
                return null;
            }
            List<PhotoSize> sizes = photos.getPhotos().getFirst();
            if (sizes == null || sizes.isEmpty()) {
                return null;
            }
            return sizes.stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .map(PhotoSize::getFileId)
                    .orElse(null);
        } catch (TelegramApiException e) {
            log.warn("Failed to get user avatar file id for {}", telegramUserId, e);
            return null;
        }
    }

    @Override
    public List<String> getUserAvatarFileIds(Long telegramUserId) {
        if (telegramUserId == null) {
            return List.of();
        }
        List<String> fileIds = new ArrayList<>();
        int offset = 0;
        try {
            while (offset < MAX_AVATAR_FETCH) {
                GetUserProfilePhotos request = GetUserProfilePhotos.builder()
                        .userId(telegramUserId)
                        .offset(offset)
                        .limit(MAX_AVATAR_FETCH)
                        .build();
                UserProfilePhotos photos = telegramClient.execute(request);
                if (photos == null || photos.getPhotos() == null || photos.getPhotos().isEmpty()) {
                    break;
                }
                for (List<PhotoSize> sizes : photos.getPhotos()) {
                    if (sizes == null || sizes.isEmpty()) {
                        continue;
                    }
                    sizes.stream()
                            .max(Comparator.comparing(PhotoSize::getFileSize))
                            .map(PhotoSize::getFileId)
                            .filter(id -> id != null && !id.isBlank())
                            .filter(id -> fileIds.size() < MAX_AVATAR_FETCH)
                            .ifPresent(fileIds::add);
                }
                offset += photos.getPhotos().size();
                if (offset >= photos.getTotalCount()) {
                    break;
                }
            }
        } catch (TelegramApiException e) {
            log.warn("Failed to get user avatar list for {}", telegramUserId, e);
        }
        log.info("用户头像集合拉取: userId={}, 取到 {} 张", telegramUserId, fileIds.size());
        return fileIds;
    }

    @Override
    public void sendPhotoAlbumByFileIds(Long chatId, Integer replyToMessageId, List<String> fileIds, String caption) {
        if (chatId == null || fileIds == null || fileIds.isEmpty()) {
            return;
        }
        for (int start = 0; start < fileIds.size(); start += MAX_ALBUM_SIZE) {
            List<String> batch = fileIds.subList(start, Math.min(start + MAX_ALBUM_SIZE, fileIds.size()));
            // SendMediaGroup 要求 2-10 张，单张（含尾批只剩 1 张）走 SendPhoto
            if (batch.size() == 1) {
                sendSinglePhotoByFileId(chatId, start == 0 ? replyToMessageId : null,
                        start == 0 ? caption : null, batch.get(0));
                continue;
            }
            List<InputMediaPhoto> media = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                InputMediaPhoto.InputMediaPhotoBuilder<?, ?> builder = InputMediaPhoto.builder().media(batch.get(i));
                if (start == 0 && i == 0 && caption != null && !caption.isBlank()) {
                    builder.caption(caption);
                }
                media.add(builder.build());
            }
            SendMediaGroup.SendMediaGroupBuilder<?, ?> groupBuilder = SendMediaGroup.builder()
                    .chatId(chatId)
                    .medias(media);
            if (start == 0 && replyToMessageId != null) {
                groupBuilder.replyToMessageId(replyToMessageId);
            }
            try {
                telegramClient.execute(groupBuilder.build());
                eventPublisher.publishEvent(new UpdateHandledEvent("album", chatId));
            } catch (TelegramApiException e) {
                log.error("Failed to send photo album to chat {}", chatId, e);
            }
        }
    }

    @Override
    public ChatFullInfo getChatFullInfo(Long chatId) {
        if (chatId == null) {
            return null;
        }
        try {
            return telegramClient.execute(GetChat.builder().chatId(chatId).build());
        } catch (TelegramApiException e) {
            log.warn("Failed to get chat full info for {}", chatId, e);
            return null;
        }
    }

    private void sendSinglePhotoByFileId(Long chatId, Integer replyToMessageId, String caption, String fileId) {
        SendPhoto.SendPhotoBuilder<?, ?> builder = SendPhoto.builder()
                .chatId(chatId)
                .photo(new InputFile(fileId));
        if (caption != null && !caption.isBlank()) {
            builder.caption(caption);
        }
        if (replyToMessageId != null) {
            builder.replyToMessageId(replyToMessageId);
        }
        try {
            telegramClient.execute(builder.build());
            eventPublisher.publishEvent(new UpdateHandledEvent("photo", chatId));
        } catch (TelegramApiException e) {
            log.error("Failed to send photo by file id to chat {}", chatId, e);
        }
    }

    @Override
    public byte[] downloadFileBytes(String fileId) {
        if (fileId == null || fileId.isBlank() || botToken == null || botToken.isBlank()) {
            return null;
        }
        try {
            org.telegram.telegrambots.meta.api.objects.File telegramFile = telegramClient.execute(
                    GetFile.builder().fileId(fileId).build()
            );
            if (telegramFile == null || telegramFile.getFilePath() == null || telegramFile.getFilePath().isBlank()) {
                return null;
            }
            String fileUrl = TELEGRAM_FILE_BASE_URL + botToken + "/" + telegramFile.getFilePath();
            return HttpUtil.getBytes(fileUrl, null);
        } catch (TelegramApiException e) {
            log.warn("Failed to download telegram file bytes for fileId={}", fileId, e);
            return null;
        }
    }

    @Override
    public void editInlineMessageText(String inlineMessageId, String text, String parseMode, boolean clearReplyMarkup) {
        if (inlineMessageId == null || inlineMessageId.isBlank() || text == null || text.isBlank()) {
            return;
        }
        EditMessageText.EditMessageTextBuilder<?, ?> builder = EditMessageText.builder()
                .inlineMessageId(inlineMessageId)
                .text(text);
        if (parseMode != null && !parseMode.isBlank()) {
            builder.parseMode(parseMode);
        }
        if (clearReplyMarkup) {
            builder.replyMarkup(new InlineKeyboardMarkup(List.of()));
        }
        try {
            telegramClient.execute(builder.build());
            eventPublisher.publishEvent(new UpdateHandledEvent("edit_inline_text", null));
        } catch (TelegramApiException e) {
            log.error("Failed to edit inline message text {}", inlineMessageId, e);
        }
    }

    @Override
    public boolean editInlineMessageTextWithNoopButton(String inlineMessageId, String text, String parseMode, String buttonText) {
        if (inlineMessageId == null || inlineMessageId.isBlank() || text == null || text.isBlank()) {
            return false;
        }
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text(buttonText == null || buttonText.isBlank() ? "处理中..." : buttonText)
                .callbackData(BotConstant.CALLBACK_ACTION_NOOP)
                .build();
        EditMessageText.EditMessageTextBuilder<?, ?> builder = EditMessageText.builder()
                .inlineMessageId(inlineMessageId)
                .text(text)
                .replyMarkup(new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button))));
        if (parseMode != null && !parseMode.isBlank()) {
            builder.parseMode(parseMode);
        }
        try {
            telegramClient.execute(builder.build());
            eventPublisher.publishEvent(new UpdateHandledEvent("edit_inline_text", null));
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to edit inline message text with noop button {}", inlineMessageId, e);
            return false;
        }
    }

    @Override
    public boolean editInlineMessagePhoto(String inlineMessageId, String photoUrl, String caption, String parseMode) {
        if (inlineMessageId == null || inlineMessageId.isBlank() || photoUrl == null || photoUrl.isBlank()) {
            return false;
        }
        InputMediaPhoto.InputMediaPhotoBuilder<?, ?> mediaBuilder = InputMediaPhoto.builder().media(photoUrl);
        if (caption != null && !caption.isBlank()) {
            mediaBuilder.caption(caption);
        }
        if (parseMode != null && !parseMode.isBlank()) {
            mediaBuilder.parseMode(parseMode);
        }
        EditMessageMedia editMessageMedia = EditMessageMedia.builder()
                .inlineMessageId(inlineMessageId)
                .media(mediaBuilder.build())
                .replyMarkup(new InlineKeyboardMarkup(List.of()))
                .build();
        try {
            telegramClient.execute(editMessageMedia);
            eventPublisher.publishEvent(new UpdateHandledEvent("edit_inline_media", null));
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to edit inline message media {}, media={}", inlineMessageId, abbreviateMedia(photoUrl), e);
            return false;
        }
    }

    @Override
    public boolean editInlineMessageVideo(String inlineMessageId, String videoUrl, String caption, String parseMode) {
        if (inlineMessageId == null || inlineMessageId.isBlank() || videoUrl == null || videoUrl.isBlank()) {
            return false;
        }
        InputMediaVideo.InputMediaVideoBuilder<?, ?> mediaBuilder = InputMediaVideo.builder().media(videoUrl);
        if (caption != null && !caption.isBlank()) {
            mediaBuilder.caption(caption);
        }
        if (parseMode != null && !parseMode.isBlank()) {
            mediaBuilder.parseMode(parseMode);
        }
        EditMessageMedia editMessageMedia = EditMessageMedia.builder()
                .inlineMessageId(inlineMessageId)
                .media(mediaBuilder.build())
                .build();
        try {
            telegramClient.execute(editMessageMedia);
            eventPublisher.publishEvent(new UpdateHandledEvent("edit_inline_video", null));
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to edit inline message video {}, media={}", inlineMessageId, abbreviateMedia(videoUrl), e);
            return false;
        }
    }

    private String abbreviateMedia(String media) {
        if (media == null) {
            return "null";
        }
        if (media.length() <= 80) {
            return media;
        }
        return media.substring(0, 77) + "...";
    }

    @Override
    public void answerInline(String inlineQueryId, List<InlineQueryResult> results, String nextOffset) {
        if (inlineQueryId == null || inlineQueryId.isBlank()) {
            return;
        }
        List<InlineQueryResult> safeResults = results == null ? List.of() : results;
        AnswerInlineQuery.AnswerInlineQueryBuilder<?, ?> builder = AnswerInlineQuery.builder()
                .inlineQueryId(inlineQueryId)
                .results(safeResults)
                .cacheTime(0)
                .isPersonal(true);
        if (nextOffset != null) {
            builder.nextOffset(nextOffset);
        }
        AnswerInlineQuery answerInlineQuery = builder.build();
        try {
            telegramClient.execute(answerInlineQuery);
            log.info("answer inline queryId={}, results={}, nextOffset={}", inlineQueryId, safeResults.size(), nextOffset);
            eventPublisher.publishEvent(new UpdateHandledEvent("inline_query", null));
        } catch (TelegramApiException e) {
            log.error("Failed to answer inline query {}", inlineQueryId, e);
        }
    }
}
