package com.github.anicmv.telegrambot.model;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 机器人用户资料模型。
 */
public record BotUserProfile(
        Long userId,
        String username,
        String nickname,
        Long telegramId,
        String avatarFileId,
        byte[] avatarData
) {
}
