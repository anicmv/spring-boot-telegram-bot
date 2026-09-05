package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.utils.BotUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.ChatFullInfo;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * @author anicmv
 * @date 2026/9/5
 * @description /info 命令处理器：查看用户、群组、频道信息。
 * 回复某条消息查该用户；群内直接发查本群/频道；私聊直接发查自己。
 * 简介仅对与机器人私聊过的用户可见。
 */
@Log4j2
@BotCommand(value = BotConstant.CMD_INFO, description = "查看信息：回复消息查用户，群内直发查群/频道")
@Component
public class InfoCommandHandler implements BotCommandHandler {

    private static final String UNKNOWN = "—";

    private final Messenger messenger;

    public InfoCommandHandler(Messenger messenger) {
        this.messenger = messenger;
    }

    @Override
    public void execute(BotContext context) {
        Message message = context.message();
        Integer replyTo = message != null ? message.getMessageId() : null;
        User repliedUser = message != null && message.getReplyToMessage() != null
                ? message.getReplyToMessage().getFrom() : null;
        String html;
        if (repliedUser != null) {
            html = formatUser(repliedUser, context.chatId());
        } else if (context.chatId() != null && context.chatId() < 0) {
            html = formatChat(context.chatId());
        } else if (message != null && message.getFrom() != null) {
            html = formatUser(message.getFrom(), null);
        } else {
            messenger.sendText(context.chatId(), "无法确定查询对象。");
            return;
        }
        if (replyTo != null && repliedUser != null) {
            messenger.sendReplyHtmlText(context.chatId(), replyTo, html);
        } else {
            messenger.sendHtmlText(context.chatId(), html);
        }
    }

    String formatUser(User user, Long currentChatId) {
        StringBuilder builder = new StringBuilder();
        builder.append("🆔 ID: ").append(user.getId()).append('\n');
        builder.append("🎨 昵称: ").append(BotUtil.escapeHtml(displayName(user))).append('\n');
        builder.append("🏷 用户名: ")
                .append(isBlank(user.getUserName()) ? UNKNOWN : "@" + BotUtil.escapeHtml(user.getUserName()))
                .append('\n');
        if (Boolean.TRUE.equals(user.getIsBot())) {
            builder.append("🤖 机器人: ✅\n");
        }
        if (currentChatId != null && currentChatId < 0) {
            builder.append("👥 所在群聊 ID: ").append(currentChatId).append('\n');
        }
        ChatFullInfo self = messenger.getChatFullInfo(user.getId());
        String bio = self == null ? null
                : !isBlank(self.getBio()) ? self.getBio() : self.getDescription();
        builder.append("\n🖋 简介:\n")
                .append(isBlank(bio) ? "<i>无（用户简介仅在与机器人私聊过后可见）</i>" : BotUtil.escapeHtml(bio));
        return builder.toString();
    }

    String formatChat(Long chatId) {
        ChatFullInfo chat = messenger.getChatFullInfo(chatId);
        if (chat == null) {
            return "🆔 ID: " + chatId + "\n<i>无法获取该会话信息（机器人可能不在群内或无权限）</i>";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("🆔 ID: ").append(chat.getId()).append('\n');
        builder.append("🎨 名称: ")
                .append(isBlank(chat.getTitle()) ? UNKNOWN : BotUtil.escapeHtml(chat.getTitle())).append('\n');
        builder.append("🏷 用户名: ")
                .append(isBlank(chat.getUserName()) ? UNKNOWN : "@" + BotUtil.escapeHtml(chat.getUserName()))
                .append('\n');
        builder.append("📌 类型: ").append(chatTypeText(chat.getType())).append('\n');
        builder.append("\n🖋 简介:\n")
                .append(isBlank(chat.getDescription()) ? "<i>无</i>" : BotUtil.escapeHtml(chat.getDescription()));
        return builder.toString();
    }

    private String chatTypeText(String type) {
        if (type == null) {
            return UNKNOWN;
        }
        return switch (type) {
            case "private" -> "私聊";
            case "group" -> "小群（≤200 人）";
            case "supergroup" -> "超级群";
            case "channel" -> "频道";
            default -> type;
        };
    }

    private String displayName(User user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? "未知" : fullName;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
