package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.service.BotUserProfileService;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.BotUserProfile;
import com.github.anicmv.telegrambot.model.InlineButton;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.List;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description /start 命令处理器。
 */
@BotCommand(value = BotConstant.CMD_START, description = "初始化/注册并展示用户信息")
@Component
public class StartCommandHandler implements BotCommandHandler {

    private final Messenger messenger;
    private final BotUserProfileService botUserProfileService;

    public StartCommandHandler(Messenger messenger, BotUserProfileService botUserProfileService) {
        this.messenger = messenger;
        this.botUserProfileService = botUserProfileService;
    }

    @Override
    public void execute(BotContext context) {
        if (context.message() == null || context.message().getFrom() == null) {
            return;
        }
        Long chatId = context.chatId();
        Integer replyToMessageId = context.message().getMessageId();
        User from = context.message().getFrom();
        // 全局自动注册（@Order(-1000)）先于命令链执行并已 upsert，不能用“记录是否存在”判断首次，
        // 改为识别“记录是否在近期创建”，以还原首次注册体验。
        boolean existed = !botUserProfileService.registeredRecently(from.getId());
        BotUserProfile profile = persistUserProfile(from);
        if (existed) {
            sendProfileMessage(chatId, replyToMessageId, profile.avatarFileId(), buildRegisteredText(profile));
        } else {
            sendProfileMessage(chatId, replyToMessageId, profile.avatarFileId(), buildFirstRegisterText(profile));
            if (replyToMessageId != null) {
                messenger.sendReplyTextWithInlineButtons(
                        chatId,
                        replyToMessageId,
                        "机器人已启动。点下面按钮测试回调。",
                        List.of(new InlineButton("Ping", BotConstant.CALLBACK_ACTION_PING + ":hello"))
                );
            } else {
                messenger.sendTextWithInlineButtons(
                        chatId,
                        "机器人已启动。点下面按钮测试回调。",
                        List.of(new InlineButton("Ping", BotConstant.CALLBACK_ACTION_PING + ":hello"))
                );
            }
        }
    }

    private BotUserProfile persistUserProfile(User from) {
        String avatarFileId = messenger.getUserAvatarFileId(from.getId());
        byte[] avatarData = avatarFileId == null || avatarFileId.isBlank()
                ? null
                : messenger.downloadFileBytes(avatarFileId);
        String nickname = buildNickname(from.getFirstName(), from.getLastName());
        BotUserProfile profile = new BotUserProfile(
                null,
                from.getUserName(),
                nickname,
                from.getId(),
                avatarFileId,
                avatarData
        );
        botUserProfileService.upsert(profile);
        return botUserProfileService.findByTelegramId(from.getId()).orElse(profile);
    }

    private String buildRegisteredText(BotUserProfile profile) {
        return """
                已注册。
                userId: %s
                username: %s
                nickname: %s
                telegramId: %s
                avatarFileId: %s
                """.formatted(
                profile.userId(),
                nullToDash(profile.username()),
                nullToDash(profile.nickname()),
                profile.telegramId(),
                nullToDash(profile.avatarFileId())
        );
    }

    private String buildFirstRegisterText(BotUserProfile profile) {
        return """
                注册成功。
                userId: %s
                username: %s
                nickname: %s
                telegramId: %s
                avatarFileId: %s
                """.formatted(
                profile.userId(),
                nullToDash(profile.username()),
                nullToDash(profile.nickname()),
                profile.telegramId(),
                nullToDash(profile.avatarFileId())
        );
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String buildNickname(String firstName, String lastName) {
        String fn = firstName == null ? "" : firstName.trim();
        String ln = lastName == null ? "" : lastName.trim();
        String full = (fn + " " + ln).trim();
        return full.isBlank() ? "telegram user id" : full;
    }

    private void sendProfileMessage(Long chatId, Integer replyToMessageId, String avatarFileId, String text) {
        if (avatarFileId == null || avatarFileId.isBlank()) {
            if (replyToMessageId != null) {
                messenger.sendReplyText(chatId, replyToMessageId, text);
            } else {
                messenger.sendText(chatId, text);
            }
            return;
        }
        if (replyToMessageId != null) {
            messenger.sendReplyPhotoByUrl(chatId, replyToMessageId, avatarFileId, text);
        } else {
            messenger.sendPhotoByUrl(chatId, avatarFileId, text);
        }
    }
}
