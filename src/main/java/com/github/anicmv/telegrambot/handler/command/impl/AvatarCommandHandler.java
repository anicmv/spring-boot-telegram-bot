package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * @author anicmv
 * @date 2026/9/5
 * @description /avatars 命令处理器：获取用户历史头像集合并以相册发出。
 * 回复某条消息发 /avatars 查该消息发送者；直接发 /avatars 查自己。
 */
@Log4j2
@BotCommand(value = BotConstant.CMD_AVATARS, description = "获取头像集合：/avatars 取自己的，回复消息取对方的")
@Component
public class AvatarCommandHandler implements BotCommandHandler {

    private final Messenger messenger;

    public AvatarCommandHandler(Messenger messenger) {
        this.messenger = messenger;
    }

    @Override
    public void execute(BotContext context) {
        Message message = context.message();
        User target = resolveTarget(message, context.userId());
        if (target == null || target.getId() == null) {
            messenger.sendText(context.chatId(), "无法确定目标用户，请直接发送 /avatars 查自己，或回复某条消息查对方。");
            return;
        }
        String displayName = displayName(target);
        List<String> fileIds = messenger.getUserAvatarFileIds(target.getId());
        Integer replyTo = message != null ? message.getMessageId() : null;
        if (fileIds.isEmpty()) {
            messenger.sendText(context.chatId(), displayName + " 没有可用的头像（解析到的用户 ID: "
                    + target.getId() + "）。");
            return;
        }
        String caption = "📸 " + displayName + " 的头像集合 ｜ 共 " + fileIds.size() + " 张";
        messenger.sendPhotoAlbumByFileIds(context.chatId(), replyTo, fileIds, caption);
    }

    private User resolveTarget(Message message, Long fallbackUserId) {
        if (message != null && message.getReplyToMessage() != null
                && message.getReplyToMessage().getFrom() != null) {
            return message.getReplyToMessage().getFrom();
        }
        if (message != null && message.getFrom() != null) {
            return message.getFrom();
        }
        return fallbackUserId == null ? null : User.builder()
                .id(fallbackUserId)
                .firstName("user")
                .isBot(false)
                .build();
    }

    private String displayName(User user) {
        if (user.getUserName() != null && !user.getUserName().isBlank()) {
            return "@" + user.getUserName();
        }
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? "ID " + user.getId() : fullName;
    }
}
