package com.github.anicmv.telegrambot.service;

import com.github.anicmv.telegrambot.config.BotProperties;
import org.springframework.stereotype.Service;

/**
 * @author anicmv
 * @date 2026/5/1 16:33
 * @description AI 能力访问控制，按 Telegram 用户黑名单拦截。
 */
@Service
public class AiAccessControlService {

    private static final String BLOCKED_MESSAGE = "你没有权限使用 AI 功能。";

    private final BotProperties botProperties;

    public AiAccessControlService(BotProperties botProperties) {
        this.botProperties = botProperties;
    }

    public boolean isBlocked(Long telegramUserId) {
        return telegramUserId != null
                && botProperties.getAi() != null
                && botProperties.getAi().getBlacklistUserIds() != null
                && botProperties.getAi().getBlacklistUserIds().contains(telegramUserId);
    }

    public String blockedMessage() {
        return BLOCKED_MESSAGE;
    }
}
