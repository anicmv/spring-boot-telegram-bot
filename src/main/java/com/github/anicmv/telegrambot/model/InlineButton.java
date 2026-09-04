package com.github.anicmv.telegrambot.model;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 内联按钮模型，抽象按钮文案和回调数据。
 */
public record InlineButton(String text, String callbackData) {
}
