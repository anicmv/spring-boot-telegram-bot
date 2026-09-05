package com.github.anicmv.telegrambot.event;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 机器人出站调用完成事件，用于日志和观测扩展。
 */
public record UpdateHandledEvent(Kind updateKind, Long chatId) {

    /**
     * 已完成的 Telegram 调用类型。
     */
    public enum Kind {
        // 文本消息
        MESSAGE,
        REPLY_MESSAGE,
        HTML_MESSAGE,
        REPLY_HTML_MESSAGE,
        MARKDOWN_V2_MESSAGE,
        REPLY_MARKDOWN_V2_MESSAGE,
        MESSAGE_WITH_INLINE_BUTTONS,
        REPLY_MESSAGE_WITH_INLINE_BUTTONS,
        MESSAGE_WITH_SWITCH_INLINE_BUTTON,
        REPLY_MESSAGE_WITH_SWITCH_INLINE_BUTTON,
        // 媒体消息
        PHOTO,
        REPLY_PHOTO,
        VIDEO,
        REPLY_VIDEO,
        DOCUMENT,
        REPLY_DOCUMENT,
        ALBUM,
        UPLOAD_PHOTO_AND_FILE_ID,
        UPLOAD_PHOTO_BYTES,
        // 编辑与删除
        DELETE_MESSAGE,
        EDIT_MESSAGE_TEXT,
        EDIT_INLINE_TEXT,
        EDIT_INLINE_MEDIA,
        EDIT_INLINE_VIDEO,
        // 回调与 inline 应答
        CALLBACK,
        INLINE_QUERY
    }
}
