package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.utils.BotUtil;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.Replier;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.config.MafProperties;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description /maf 命令处理器，计算并返回男娘指数。
 */
@BotCommand(value = BotConstant.CMD_MAF, description = "男娘指数（回复某人消息可测对方）")
@Component
public class MaleAndFemaleCommandHandler implements BotCommandHandler {

    private final Messenger messenger;
    private final MafProperties mafProperties;

    public MaleAndFemaleCommandHandler(Messenger messenger, MafProperties mafProperties) {
        this.messenger = messenger;
        this.mafProperties = mafProperties;
    }

    @Override
    public void execute(BotContext context) {
        Message message = context.message();
        if (message == null) {
            return;
        }
        Replier replier = Replier.of(context, messenger);
        Message replyTo = message.getReplyToMessage();
        User targetUser = replyTo != null ? replyTo.getFrom() : message.getFrom();
        if (targetUser == null) {
            replier.text("未找到目标用户，无法测定。");
            return;
        }
        String fullName = BotUtil.formatUserName(targetUser);
        int index = getMafIndex(targetUser.getId(), fullName, targetUser.getUserName());
        String clickableUser = BotUtil.mentionMarkdownV2(targetUser);
        String text = BotUtil.escapeMarkdownV2("🔮 男娘指数测定报告 🔮")
                + "\n\n"
                + BotUtil.escapeMarkdownV2("👤 用户：")
                + clickableUser
                + "\n"
                + BotUtil.escapeMarkdownV2("💖 娘度：" + getLevel(index))
                + "\n\n"
                + BotUtil.escapeMarkdownV2(getComment(index));
        replier.markdownV2(text);
    }

    private int getMafIndex(Long userId, String fullName, String userName) {
        if (userId != null && mafProperties.getCustomLevels() != null) {
            Integer customLevel = mafProperties.getCustomLevels().get(userId);
            if (customLevel != null) {
                return Math.max(-1, Math.min(100, customLevel));
            }
        }
        return calculateMafIndex(userId == null ? 0L : userId, fullName, userName);
    }

    private int calculateMafIndex(long userId, String fullName, String userName) {
        String raw = userId + "|" + safe(fullName) + "|" + safe(userName);
        long hash = 0;
        for (int i = 0; i < raw.length(); i++) {
            hash = hash * 31 + raw.charAt(i);
            hash ^= (hash >>> 16);
        }
        hash ^= Long.reverseBytes(userId);
        hash = hash * 6364136223846793005L + 1442695040888963407L;
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= (hash >>> 33);
        return (int) Math.abs(hash % 101);
    }

    private String getLevel(int index) {
        int filled = (index + 9) / 10;
        if (filled > 10) {
            filled = 10;
        }
        return "💗".repeat(Math.max(0, filled)) + "🤍".repeat(10 - filled);
    }

    private String getComment(int index) {
        if (index == -1) {
            return "🪨 钢铁直男，毫无破绽。";
        }
        if (index <= 5) {
            return "🌸 刚刚觉醒，偶尔会被叫小姐姐！";
        }
        if (index <= 15) {
            return "💕 初露锋芒，女装大佬预备役！";
        }
        if (index <= 30) {
            return "🎀 渐入佳境，不化妆也能以假乱真！";
        }
        if (index <= 45) {
            return "✨ 小有成就，路人已分不清性别~";
        }
        if (index <= 55) {
            return "🌺 登堂入室，可攻可受可卖萌！";
        }
        if (index <= 70) {
            return "💐 炉火纯青，女装起来秒杀真·女生！";
        }
        if (index <= 85) {
            return "👗 出神入化，行走的荷尔蒙收割机！";
        }
        if (index <= 95) {
            return "💫 登峰造极，直男弯了，弯男更弯了！！";
        }
        return "🎐 超凡入圣！传说中能让石头开花的绝世男娘！！！";
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
