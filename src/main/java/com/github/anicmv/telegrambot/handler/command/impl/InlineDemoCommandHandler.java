package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description /inline_demo 命令处理器，演示 switch inline query 按钮。
 */
@BotCommand(value = BotConstant.CMD_INLINE, description = "打开 inline 输入演示")
@Component
public class InlineDemoCommandHandler implements BotCommandHandler {

    private final Messenger messenger;

    public InlineDemoCommandHandler(Messenger messenger) {
        this.messenger = messenger;
    }

    @Override
    public void execute(BotContext context) {
        if (context.message() != null && context.message().getMessageId() != null) {
            messenger.sendReplyTextWithSwitchInlineButton(
                    context.chatId(),
                    context.message().getMessageId(),
                    "点击按钮后，输入框会自动填入 @你的Bot（空查询）。",
                    "↗ 内联输入示例",
                    ""
            );
            return;
        }
        messenger.sendTextWithSwitchInlineButton(
                context.chatId(),
                "点击按钮后，输入框会自动填入 @你的Bot（空查询）。",
                "↗ 内联输入示例",
                ""
        );
    }
}
