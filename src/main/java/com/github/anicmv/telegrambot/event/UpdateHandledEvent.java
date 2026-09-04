package com.github.anicmv.telegrambot.event;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 更新处理完成事件，用于日志和观测扩展。
 */
public record UpdateHandledEvent(String updateKind, Long userId) {
}
