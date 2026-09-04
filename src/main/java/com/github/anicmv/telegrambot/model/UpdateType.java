package com.github.anicmv.telegrambot.model;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description Update 类型枚举。
 */
public enum UpdateType {
    MESSAGE,
    CALLBACK_QUERY,
    INLINE_QUERY,
    CHOSEN_INLINE_QUERY,
    UNKNOWN
}
