package com.github.anicmv.telegrambot.messenger;

import com.github.anicmv.telegrambot.model.InlineButton;
import org.telegram.telegrambots.meta.api.objects.chat.ChatFullInfo;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;

import java.util.List;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 机器人对外消息能力抽象（信使），隔离 Telegram SDK。
 */
public interface Messenger {

    void sendText(Long chatId, String text);

    void sendReplyText(Long chatId, Integer replyToMessageId, String text);

    Integer sendReplyTextAndReturnMessageId(Long chatId, Integer replyToMessageId, String text);

    void sendHtmlText(Long chatId, String text);

    Integer sendHtmlTextAndReturnMessageId(Long chatId, String text);

    void sendReplyHtmlText(Long chatId, Integer replyToMessageId, String text);

    Integer sendReplyHtmlTextAndReturnMessageId(Long chatId, Integer replyToMessageId, String text);

    void sendMarkdownV2Text(Long chatId, String text);

    void sendReplyMarkdownV2Text(Long chatId, Integer replyToMessageId, String text);

    Integer sendReplyMarkdownV2TextAndReturnMessageId(Long chatId, Integer replyToMessageId, String text);

    void sendTextWithInlineButtons(Long chatId, String text, List<InlineButton> buttons);

    void sendReplyTextWithInlineButtons(Long chatId, Integer replyToMessageId, String text, List<InlineButton> buttons);

    void sendTextWithSwitchInlineButton(Long chatId, String text, String buttonText, String inlineQuery);

    void sendReplyTextWithSwitchInlineButton(Long chatId, Integer replyToMessageId, String text, String buttonText, String inlineQuery);

    void sendPhotoByUrl(Long chatId, String photoUrl, String caption);

    void sendPhotoByUrl(Long chatId, String photoUrl, String caption, String parseMode);

    void sendReplyPhotoByUrl(Long chatId, Integer replyToMessageId, String photoUrl, String caption);

    boolean sendVideoByPath(Long chatId, String videoPath, String caption);

    boolean sendReplyVideoByPath(Long chatId, Integer replyToMessageId, String videoPath, String caption);

    boolean sendDocumentByPath(Long chatId, String documentPath, String caption);

    boolean sendReplyDocumentByPath(Long chatId, Integer replyToMessageId, String documentPath, String caption);

    String uploadPhotoAndEchoFileId(Long channelId, String urlOrPath);

    /**
     * 上传照片字节到指定会话换取新 file_id，发送后立即删除消息；用于刷新过期的 file_reference。
     */
    String uploadPhotoBytes(Long chatId, byte[] data);

    String getUserAvatarFileId(Long telegramUserId);

    /**
     * 获取用户全部历史头像的 file_id（每张取最大尺寸，新的在前，最多 100 张）。
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

    void editMessageText(Long chatId, Integer messageId, String text, String parseMode);

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
