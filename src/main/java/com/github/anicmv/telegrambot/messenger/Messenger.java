package com.github.anicmv.telegrambot.messenger;

import com.github.anicmv.telegrambot.model.InlineButton;
import org.telegram.telegrambots.meta.api.objects.chat.ChatFullInfo;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;

import java.util.List;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 机器人对外消息能力抽象（信使），隔离 Telegram SDK。
 * 文本消息以 {@link #sendTextMessage(TextSpec)} 为核心，历史方法保留为 default 快捷入口。
 */
public interface Messenger {

    /**
     * 发送文本消息（核心入口）。
     *
     * @return 成功时返回新消息 messageId，失败返回 null
     */
    Integer sendTextMessage(TextSpec spec);

    default void sendText(Long chatId, String text) {
        sendTextMessage(TextSpec.of(chatId, text));
    }

    default void sendReplyText(Long chatId, Integer replyToMessageId, String text) {
        sendTextMessage(TextSpec.of(chatId, text).replyTo(replyToMessageId));
    }

    default Integer sendReplyTextAndReturnMessageId(Long chatId, Integer replyToMessageId, String text) {
        return sendTextMessage(TextSpec.of(chatId, text).replyTo(replyToMessageId));
    }

    default void sendHtmlText(Long chatId, String text) {
        sendTextMessage(TextSpec.of(chatId, text).html());
    }

    default Integer sendHtmlTextAndReturnMessageId(Long chatId, String text) {
        return sendTextMessage(TextSpec.of(chatId, text).html());
    }

    default void sendReplyHtmlText(Long chatId, Integer replyToMessageId, String text) {
        sendTextMessage(TextSpec.of(chatId, text).html().replyTo(replyToMessageId));
    }

    default Integer sendReplyHtmlTextAndReturnMessageId(Long chatId, Integer replyToMessageId, String text) {
        return sendTextMessage(TextSpec.of(chatId, text).html().replyTo(replyToMessageId));
    }

    default void sendMarkdownV2Text(Long chatId, String text) {
        sendTextMessage(TextSpec.of(chatId, text).markdownV2());
    }

    default void sendReplyMarkdownV2Text(Long chatId, Integer replyToMessageId, String text) {
        sendTextMessage(TextSpec.of(chatId, text).markdownV2().replyTo(replyToMessageId));
    }

    default Integer sendReplyMarkdownV2TextAndReturnMessageId(Long chatId, Integer replyToMessageId, String text) {
        return sendTextMessage(TextSpec.of(chatId, text).markdownV2().replyTo(replyToMessageId));
    }

    default void sendTextWithInlineButtons(Long chatId, String text, List<InlineButton> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            return;
        }
        sendTextMessage(TextSpec.of(chatId, text).callbackButtons(buttons));
    }

    default void sendReplyTextWithInlineButtons(Long chatId, Integer replyToMessageId, String text, List<InlineButton> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            return;
        }
        sendTextMessage(TextSpec.of(chatId, text).callbackButtons(buttons).replyTo(replyToMessageId));
    }

    default void sendTextWithSwitchInlineButton(Long chatId, String text, String buttonText, String inlineQuery) {
        sendTextMessage(TextSpec.of(chatId, text).switchInline(buttonText, inlineQuery));
    }

    default void sendReplyTextWithSwitchInlineButton(Long chatId, Integer replyToMessageId, String text, String buttonText, String inlineQuery) {
        sendTextMessage(TextSpec.of(chatId, text).switchInline(buttonText, inlineQuery).replyTo(replyToMessageId));
    }

    void sendPhotoByUrl(Long chatId, String photoUrl, String caption);

    void sendPhotoByUrl(Long chatId, String photoUrl, String caption, String parseMode);

    void sendReplyPhotoByUrl(Long chatId, Integer replyToMessageId, String photoUrl, String caption);

    boolean sendVideoByPath(Long chatId, String videoPath, String caption);

    boolean sendReplyVideoByPath(Long chatId, Integer replyToMessageId, String videoPath, String caption);

    boolean sendDocumentByPath(Long chatId, String documentPath, String caption);

    boolean sendReplyDocumentByPath(Long chatId, Integer replyToMessageId, String documentPath, String caption);

    /**
     * 上传图片到中转 channel 换取 file_id，并额外向该 channel 回发一条 file_id 文本消息存档；失败返回 null。
     */
    String uploadPhotoViaChannel(Long channelId, String urlOrPath);

    /**
     * 上传照片字节到指定会话换取新 file_id，发送后立即删除消息；用于刷新过期的 file_reference。
     */
    String uploadPhotoBytes(Long chatId, byte[] data);

    String getUserAvatarFileId(Long telegramUserId);

    /**
     * 获取用户全部历史头像的 file_id（每张取最大尺寸，新的在前，最多 100 张）。
     * 空列表表示该账号确实没有头像；返回 null 表示目标账号不存在（已注销或 ID 已变更）。
     */
    List<String> getUserAvatarFileIds(Long telegramUserId);

    /**
     * 以相册（每 10 张一组）发送图片集合，caption 挂在第一张上。
     */
    void sendPhotoAlbumByFileIds(Long chatId, Integer replyToMessageId, List<String> fileIds, String caption);

    /**
     * GetChat 拉取会话完整信息（群/频道简介、bio 等）；失败或无权限返回 null。
     */
    ChatFullInfo getChatFullInfo(Long chatId);

    byte[] downloadFileBytes(String fileId);

    void deleteMessageSilently(Long chatId, Integer messageId);

    default void editMessageText(Long chatId, Integer messageId, String text, String parseMode) {
        editMessageText(chatId, messageId, text, parseMode, null);
    }

    /**
     * 编辑消息文本并可选地变更按钮：buttons 为 null 保持原按钮不变，空列表清除按钮，非空则替换为该组按钮。
     */
    void editMessageText(Long chatId, Integer messageId, String text, String parseMode, List<InlineButton> buttons);

    default void editInlineMessageText(String inlineMessageId, String text, String parseMode) {
        editInlineMessageText(inlineMessageId, text, parseMode, false);
    }

    void editInlineMessageText(String inlineMessageId, String text, String parseMode, boolean clearReplyMarkup);

    boolean editInlineMessageTextWithNoopButton(String inlineMessageId, String text, String parseMode, String buttonText);

    /**
     * @return true 表示编辑成功；false 表示 API 异常（如头像 file_reference 过期）
     */
    boolean editInlineMessagePhoto(String inlineMessageId, String photoUrl, String caption, String parseMode);

    boolean editInlineMessageVideo(String inlineMessageId, String videoUrl, String caption, String parseMode);

    void answerCallback(String callbackQueryId, String text);

    void answerInline(String inlineQueryId, List<InlineQueryResult> results, String nextOffset);
}
