package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.service.BotUserProfileService;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.Replier;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.BotUserProfile;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * @author anicmv
 * @date 2026/3/22
 * @description 回复某人消息时，将该用户注册进红娘系统用户池。
 */
@BotCommand(value = BotConstant.CMD_MATCHMAKER_REGISTER, description = "回复某人并注册到姻缘用户池")
@Component
public class MatchmakerRegisterCommandHandler implements BotCommandHandler {

    private static final long DELETE_DELAY_SECONDS = 5L;

    private final Messenger messenger;
    private final BotUserProfileService botUserProfileService;
    private final TaskScheduler botScheduler;

    public MatchmakerRegisterCommandHandler(Messenger messenger,
                                           BotUserProfileService botUserProfileService,
                                           @Qualifier("botScheduler") TaskScheduler botScheduler) {
        this.messenger = messenger;
        this.botUserProfileService = botUserProfileService;
        this.botScheduler = botScheduler;
    }

    @Override
    public void execute(BotContext context) {
        Message message = context.message();
        if (message == null || message.getMessageId() == null) {
            return;
        }
        Message replyToMessage = message.getReplyToMessage();
        Replier replier = Replier.of(context, messenger);
        if (replyToMessage == null || replyToMessage.getFrom() == null) {
            Integer replyMessageId = replier.textAndReturnId("请用这个命令回复目标用户的消息。");
            scheduleDelete(context.chatId(), replyMessageId);
            return;
        }

        User targetUser = replyToMessage.getFrom();
        Optional<BotUserProfile> existing = botUserProfileService.findByTelegramId(targetUser.getId());
        String avatarFileId = messenger.getUserAvatarFileId(targetUser.getId());
        byte[] avatarData = avatarFileId == null || avatarFileId.isBlank()
                ? null
                : messenger.downloadFileBytes(avatarFileId);
        BotUserProfile profile = new BotUserProfile(
                existing.map(BotUserProfile::userId).orElse(null),
                targetUser.getUserName(),
                buildNickname(targetUser.getFirstName(), targetUser.getLastName()),
                targetUser.getId(),
                avatarFileId,
                avatarData
        );
        botUserProfileService.upsert(profile);

        String text = existing.isPresent()
                ? "已更新姻缘用户信息: " + formatUser(profile)
                : "已加入姻缘用户池: " + formatUser(profile);
        Integer replyMessageId = replier.textAndReturnId(text);
        scheduleDelete(context.chatId(), replyMessageId);
    }

    private String formatUser(BotUserProfile profile) {
        String nickname = profile.nickname() == null || profile.nickname().isBlank() ? "-" : profile.nickname();
        String username = profile.username() == null || profile.username().isBlank() ? "-" : "@" + profile.username();
        return nickname + " " + username + " (" + profile.telegramId() + ")";
    }

    private String buildNickname(String firstName, String lastName) {
        String fn = firstName == null ? "" : firstName.trim();
        String ln = lastName == null ? "" : lastName.trim();
        String full = (fn + " " + ln).trim();
        return full.isBlank() ? "telegram user id" : full;
    }

    private void scheduleDelete(Long chatId, Integer replyMessageId) {
        if (chatId == null || replyMessageId == null) {
            return;
        }
        botScheduler.schedule(
                () -> messenger.deleteMessageSilently(chatId, replyMessageId),
                Instant.now().plusSeconds(DELETE_DELAY_SECONDS)
        );
    }
}
