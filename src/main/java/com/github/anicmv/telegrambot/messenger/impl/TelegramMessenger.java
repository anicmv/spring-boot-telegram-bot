package com.github.anicmv.telegrambot.messenger.impl;

import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.event.UpdateHandledEvent;
import com.github.anicmv.telegrambot.event.UpdateHandledEvent.Kind;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.TextSpec;
import com.github.anicmv.telegrambot.model.InlineButton;
import com.github.anicmv.telegrambot.utils.HttpUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.GetUserProfilePhotos;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.UserProfilePhotos;
import org.telegram.telegrambots.meta.api.objects.chat.ChatFullInfo;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaVideo;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description Telegram SDK 适配实现，封装消息发送与回调响应。
 * 写操作统一经由 {@link #execute} 执行：成功发布观测事件，失败记录错误并返回 null。
 */
@Log4j2
public class TelegramMessenger implements Messenger {

    private static final String TELEGRAM_FILE_BASE_URL = "https://api.telegram.org/file/bot";
    /** GetUserProfilePhotos 官方单页上限 */
    private static final int MAX_AVATAR_FETCH = 100;
    /** SendMediaGroup 官方单组上限 */
    private static final int MAX_ALBUM_SIZE = 10;
    private static final String DEFAULT_SWITCH_BUTTON_TEXT = "Inline";
    private static final String DEFAULT_NOOP_BUTTON_TEXT = "处理中...";

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

    // ==================== 文本消息 ====================

    @Override
    public Integer sendTextMessage(TextSpec spec) {
        if (spec == null || spec.chatId() == null || blank(spec.text())) {
            return null;
        }
        var builder = SendMessage.builder()
                .chatId(spec.chatId())
                .text(spec.text());
        if (!blank(spec.parseMode())) {
            builder.parseMode(spec.parseMode());
        }
        if (spec.replyToMessageId() != null) {
            builder.replyToMessageId(spec.replyToMessageId());
        }
        if (spec.hasCallbackButtons()) {
            builder.replyMarkup(callbackKeyboard(spec.callbackButtons()));
        } else if (spec.hasSwitchInline()) {
            builder.replyMarkup(switchInlineKeyboard(spec.switchButtonText(), spec.switchInlineQuery()));
        }
        Message message = execute(() -> telegramClient.execute(builder.build()),
                spec.eventKind(), spec.chatId(), "send text message to chat " + spec.chatId());
        return message == null ? null : message.getMessageId();
    }

    // ==================== 图片 / 视频 / 文件 ====================

    @Override
    public void sendPhotoByUrl(Long chatId, String photoUrl, String caption) {
        sendPhotoByUrl(chatId, photoUrl, caption, null);
    }

    @Override
    public void sendPhotoByUrl(Long chatId, String photoUrl, String caption, String parseMode) {
        if (chatId == null || blank(photoUrl)) {
            return;
        }
        var builder = SendPhoto.builder()
                .chatId(chatId)
                .photo(new InputFile(photoUrl))
                .caption(caption);
        if (!blank(parseMode)) {
            builder.parseMode(parseMode);
        }
        execute(() -> telegramClient.execute(builder.build()),
                Kind.PHOTO, chatId, "send photo by url to chat " + chatId);
    }

    @Override
    public void sendReplyPhotoByUrl(Long chatId, Integer replyToMessageId, String photoUrl, String caption) {
        if (chatId == null || replyToMessageId == null || blank(photoUrl)) {
            return;
        }
        SendPhoto sendPhoto = SendPhoto.builder()
                .chatId(chatId)
                .replyToMessageId(replyToMessageId)
                .photo(new InputFile(photoUrl))
                .caption(caption)
                .build();
        execute(() -> telegramClient.execute(sendPhoto),
                Kind.REPLY_PHOTO, chatId,
                "send reply photo by url to chat " + chatId + ", messageId " + replyToMessageId);
    }

    @Override
    public boolean sendVideoByPath(Long chatId, String videoPath, String caption) {
        if (chatId == null || blank(videoPath)) {
            return false;
        }
        var builder = videoBuilder(chatId, videoPath, caption);
        Message message = execute(() -> telegramClient.execute(builder.build()),
                Kind.VIDEO, chatId, "send video by path to chat " + chatId + ", path=" + videoPath);
        return message != null;
    }

    @Override
    public boolean sendReplyVideoByPath(Long chatId, Integer replyToMessageId, String videoPath, String caption) {
        if (chatId == null || replyToMessageId == null || blank(videoPath)) {
            return false;
        }
        var builder = videoBuilder(chatId, videoPath, caption).replyToMessageId(replyToMessageId);
        Message message = execute(() -> telegramClient.execute(builder.build()),
                Kind.REPLY_VIDEO, chatId,
                "send reply video by path to chat " + chatId + ", messageId " + replyToMessageId + ", path=" + videoPath);
        return message != null;
    }

    private SendVideo.SendVideoBuilder<?, ?> videoBuilder(Long chatId, String videoPath, String caption) {
        SendVideo.SendVideoBuilder<?, ?> builder = SendVideo.builder()
                .chatId(chatId)
                .video(new InputFile(new File(videoPath)))
                .caption(caption)
                .parseMode("HTML")
                .supportsStreaming(true);
        VideoSize size = probeVideoSize(videoPath);
        if (size != null) {
            builder.width(size.width());
            builder.height(size.height());
        }
        return builder;
    }

    @Override
    public boolean sendDocumentByPath(Long chatId, String documentPath, String caption) {
        if (chatId == null || blank(documentPath)) {
            return false;
        }
        SendDocument sendDocument = SendDocument.builder()
                .chatId(chatId)
                .document(new InputFile(new File(documentPath)))
                .caption(caption)
                .parseMode("HTML")
                .build();
        Message message = execute(() -> telegramClient.execute(sendDocument),
                Kind.DOCUMENT, chatId, "send document by path to chat " + chatId + ", path=" + documentPath);
        return message != null;
    }

    @Override
    public boolean sendReplyDocumentByPath(Long chatId, Integer replyToMessageId, String documentPath, String caption) {
        if (chatId == null || replyToMessageId == null || blank(documentPath)) {
            return false;
        }
        SendDocument sendDocument = SendDocument.builder()
                .chatId(chatId)
                .replyToMessageId(replyToMessageId)
                .document(new InputFile(new File(documentPath)))
                .caption(caption)
                .parseMode("HTML")
                .build();
        Message message = execute(() -> telegramClient.execute(sendDocument),
                Kind.REPLY_DOCUMENT, chatId,
                "send reply document by path to chat " + chatId + ", messageId " + replyToMessageId + ", path=" + documentPath);
        return message != null;
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
                if (start == 0 && i == 0 && !blank(caption)) {
                    builder.caption(caption);
                }
                media.add(builder.build());
            }
            var groupBuilder = SendMediaGroup.builder().chatId(chatId).medias(media);
            if (start == 0 && replyToMessageId != null) {
                groupBuilder.replyToMessageId(replyToMessageId);
            }
            execute(() -> telegramClient.execute(groupBuilder.build()),
                    Kind.ALBUM, chatId, "send photo album to chat " + chatId);
        }
    }

    private void sendSinglePhotoByFileId(Long chatId, Integer replyToMessageId, String caption, String fileId) {
        var builder = SendPhoto.builder().chatId(chatId).photo(new InputFile(fileId));
        if (!blank(caption)) {
            builder.caption(caption);
        }
        if (replyToMessageId != null) {
            builder.replyToMessageId(replyToMessageId);
        }
        execute(() -> telegramClient.execute(builder.build()),
                Kind.PHOTO, chatId, "send photo by file id to chat " + chatId);
    }

    // ==================== 上传换取 file_id ====================

    @Override
    public String uploadPhotoViaChannel(Long channelId, String urlOrPath) {
        if (channelId == null || blank(urlOrPath)) {
            return null;
        }
        var builder = SendPhoto.builder().chatId(channelId);
        if (urlOrPath.startsWith("http")) {
            builder.photo(new InputFile(urlOrPath));
        } else {
            builder.photo(new InputFile(new File(urlOrPath)));
        }
        Message message = execute(() -> telegramClient.execute(builder.build()),
                Kind.UPLOAD_PHOTO_AND_FILE_ID, channelId, "upload photo and echo file id to channel " + channelId);
        String fileId = largestPhotoFileId(message);
        if (fileId == null) {
            return null;
        }
        try {
            telegramClient.execute(SendMessage.builder().chatId(channelId).text(fileId).build());
        } catch (TelegramApiException e) {
            log.error("Failed to echo file id to channel {}", channelId, e);
            return null;
        }
        return fileId;
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
        Message message = execute(() -> telegramClient.execute(sendPhoto),
                Kind.UPLOAD_PHOTO_BYTES, chatId, "upload photo bytes to chat " + chatId);
        String fileId = largestPhotoFileId(message);
        if (fileId != null) {
            deleteMessageSilently(chatId, message.getMessageId());
        }
        return fileId;
    }

    // ==================== 只读查询（不发事件） ====================

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
            return largestFileId(photos.getPhotos().getFirst());
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
                    String fileId = largestFileId(sizes);
                    if (!blank(fileId) && fileIds.size() < MAX_AVATAR_FETCH) {
                        fileIds.add(fileId);
                    }
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

    @Override
    public byte[] downloadFileBytes(String fileId) {
        if (blank(fileId) || blank(botToken)) {
            return null;
        }
        try {
            org.telegram.telegrambots.meta.api.objects.File telegramFile = telegramClient.execute(
                    GetFile.builder().fileId(fileId).build()
            );
            if (telegramFile == null || blank(telegramFile.getFilePath())) {
                return null;
            }
            String fileUrl = TELEGRAM_FILE_BASE_URL + botToken + "/" + telegramFile.getFilePath();
            return HttpUtil.getBytes(fileUrl, null);
        } catch (TelegramApiException e) {
            log.warn("Failed to download telegram file bytes for fileId={}", fileId, e);
            return null;
        }
    }

    // ==================== 编辑与删除 ====================

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
            eventPublisher.publishEvent(new UpdateHandledEvent(Kind.DELETE_MESSAGE, chatId));
        } catch (TelegramApiException ignored) {
            // Ignore on purpose: delete failure should not break business flow.
        }
    }

    @Override
    public void editMessageText(Long chatId, Integer messageId, String text, String parseMode) {
        if (chatId == null || messageId == null || blank(text)) {
            return;
        }
        var builder = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text);
        if (!blank(parseMode)) {
            builder.parseMode(parseMode);
        }
        execute(() -> telegramClient.execute(builder.build()),
                Kind.EDIT_MESSAGE_TEXT, chatId, "edit message text chatId=" + chatId + ", messageId=" + messageId);
    }

    @Override
    public void editInlineMessageText(String inlineMessageId, String text, String parseMode, boolean clearReplyMarkup) {
        if (blank(inlineMessageId) || blank(text)) {
            return;
        }
        var builder = EditMessageText.builder()
                .inlineMessageId(inlineMessageId)
                .text(text);
        if (!blank(parseMode)) {
            builder.parseMode(parseMode);
        }
        if (clearReplyMarkup) {
            builder.replyMarkup(new InlineKeyboardMarkup(List.of()));
        }
        execute(() -> telegramClient.execute(builder.build()),
                Kind.EDIT_INLINE_TEXT, null, "edit inline message text " + inlineMessageId);
    }

    @Override
    public boolean editInlineMessageTextWithNoopButton(String inlineMessageId, String text, String parseMode, String buttonText) {
        if (blank(inlineMessageId) || blank(text)) {
            return false;
        }
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text(blank(buttonText) ? DEFAULT_NOOP_BUTTON_TEXT : buttonText)
                .callbackData(BotConstant.CALLBACK_ACTION_NOOP)
                .build();
        var builder = EditMessageText.builder()
                .inlineMessageId(inlineMessageId)
                .text(text)
                .replyMarkup(new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button))));
        if (!blank(parseMode)) {
            builder.parseMode(parseMode);
        }
        Serializable result = execute(() -> telegramClient.execute(builder.build()),
                Kind.EDIT_INLINE_TEXT, null, "edit inline message text with noop button " + inlineMessageId);
        return result != null;
    }

    @Override
    public boolean editInlineMessagePhoto(String inlineMessageId, String photoUrl, String caption, String parseMode) {
        if (blank(inlineMessageId) || blank(photoUrl)) {
            return false;
        }
        var mediaBuilder = InputMediaPhoto.builder().media(photoUrl);
        if (!blank(caption)) {
            mediaBuilder.caption(caption);
        }
        if (!blank(parseMode)) {
            mediaBuilder.parseMode(parseMode);
        }
        EditMessageMedia editMessageMedia = EditMessageMedia.builder()
                .inlineMessageId(inlineMessageId)
                .media(mediaBuilder.build())
                .replyMarkup(new InlineKeyboardMarkup(List.of()))
                .build();
        Serializable result = execute(() -> telegramClient.execute(editMessageMedia),
                Kind.EDIT_INLINE_MEDIA, null,
                "edit inline message media " + inlineMessageId + ", media=" + abbreviateMedia(photoUrl));
        return result != null;
    }

    @Override
    public boolean editInlineMessageVideo(String inlineMessageId, String videoUrl, String caption, String parseMode) {
        if (blank(inlineMessageId) || blank(videoUrl)) {
            return false;
        }
        var mediaBuilder = InputMediaVideo.builder().media(videoUrl);
        if (!blank(caption)) {
            mediaBuilder.caption(caption);
        }
        if (!blank(parseMode)) {
            mediaBuilder.parseMode(parseMode);
        }
        EditMessageMedia editMessageMedia = EditMessageMedia.builder()
                .inlineMessageId(inlineMessageId)
                .media(mediaBuilder.build())
                .build();
        Serializable result = execute(() -> telegramClient.execute(editMessageMedia),
                Kind.EDIT_INLINE_VIDEO, null,
                "edit inline message video " + inlineMessageId + ", media=" + abbreviateMedia(videoUrl));
        return result != null;
    }

    // ==================== 回调 / inline 应答 ====================

    @Override
    public void answerCallback(String callbackQueryId, String text) {
        if (blank(callbackQueryId)) {
            return;
        }
        AnswerCallbackQuery answerCallbackQuery = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(text)
                .showAlert(false)
                .build();
        execute(() -> telegramClient.execute(answerCallbackQuery),
                Kind.CALLBACK, null, "answer callback " + callbackQueryId);
    }

    @Override
    public void answerInline(String inlineQueryId, List<InlineQueryResult> results, String nextOffset) {
        if (blank(inlineQueryId)) {
            return;
        }
        List<InlineQueryResult> safeResults = results == null ? List.of() : results;
        var builder = AnswerInlineQuery.builder()
                .inlineQueryId(inlineQueryId)
                .results(safeResults)
                .cacheTime(0)
                .isPersonal(true);
        if (nextOffset != null) {
            builder.nextOffset(nextOffset);
        }
        Boolean answered = execute(() -> telegramClient.execute(builder.build()),
                Kind.INLINE_QUERY, null, "answer inline query " + inlineQueryId);
        if (answered != null) {
            log.info("answer inline queryId={}, results={}, nextOffset={}", inlineQueryId, safeResults.size(), nextOffset);
        }
    }

    // ==================== 私有工具 ====================

    /**
     * 一次 Telegram API 调用；成功与否由 {@link #execute} 统一处理。
     */
    @FunctionalInterface
    private interface ApiCall<T> {
        T get() throws TelegramApiException;
    }

    /**
     * 统一执行：成功发布观测事件并返回结果，失败记录错误日志并返回 null。
     */
    private <T> T execute(ApiCall<T> call, Kind eventKind, Long chatId, String errorContext) {
        try {
            T result = call.get();
            eventPublisher.publishEvent(new UpdateHandledEvent(eventKind, chatId));
            return result;
        } catch (TelegramApiException e) {
            log.error("Failed to {}", errorContext, e);
            return null;
        }
    }

    private static InlineKeyboardMarkup callbackKeyboard(List<InlineButton> buttons) {
        InlineKeyboardRow row = new InlineKeyboardRow(buttons.stream()
                .map(button -> InlineKeyboardButton.builder()
                        .text(button.text())
                        .callbackData(button.callbackData())
                        .build())
                .toList());
        return new InlineKeyboardMarkup(List.of(row));
    }

    private static InlineKeyboardMarkup switchInlineKeyboard(String buttonText, String inlineQuery) {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text(blank(buttonText) ? DEFAULT_SWITCH_BUTTON_TEXT : buttonText)
                .switchInlineQueryCurrentChat(inlineQuery)
                .build();
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button)));
    }

    private static String largestPhotoFileId(Message message) {
        return message == null ? null : largestFileId(message.getPhoto());
    }

    private static String largestFileId(List<PhotoSize> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        return sizes.stream()
                .max(Comparator.comparing(PhotoSize::getFileSize))
                .map(PhotoSize::getFileId)
                .orElse(null);
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

    private String abbreviateMedia(String media) {
        if (media == null) {
            return "null";
        }
        if (media.length() <= 80) {
            return media;
        }
        return media.substring(0, 77) + "...";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
