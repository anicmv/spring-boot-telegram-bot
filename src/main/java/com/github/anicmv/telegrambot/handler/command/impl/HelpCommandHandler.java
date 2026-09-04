package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.handler.command.BotCommandRegistry;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description /help 命令处理器，命令列表从 {@link BotCommandRegistry} 聚合生成。
 */
@BotCommand(value = BotConstant.CMD_HELP, description = "查看帮助")
@Component
public class HelpCommandHandler implements BotCommandHandler {

    private static final String INLINE_USAGE = """
            内联输入（直接 @ 机器人）
            @你的Bot kfc / pyq / du / top / xp / husband / bili / ecy
            @你的Bot ai 你的问题
            @你的Bot dy 抖音链接（返回占位后异步解析；是否发视频由 bot.douyin.upload-video-enabled 控制）""";

    private final Messenger messenger;
    private final BotCommandRegistry commandRegistry;

    public HelpCommandHandler(Messenger messenger, @Lazy BotCommandRegistry commandRegistry) {
        this.messenger = messenger;
        this.commandRegistry = commandRegistry;
    }

    @Override
    public void execute(BotContext context) {
        StringBuilder text = new StringBuilder("🤖 可用功能");
        for (BotCommandRegistry.CommandInfo command : commandRegistry.describedCommands()) {
            text.append('\n').append(command.command()).append(' ').append(command.description());
        }
        text.append("\n\n").append(INLINE_USAGE);
        if (context.message() != null && context.message().getMessageId() != null) {
            messenger.sendReplyText(context.chatId(), context.message().getMessageId(), text.toString());
            return;
        }
        messenger.sendText(context.chatId(), text.toString());
    }
}
