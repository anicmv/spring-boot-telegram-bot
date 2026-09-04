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
 * @description /ping 命令处理器。
 */
@BotCommand(value = BotConstant.CMD_PING, description = "连通性测试（会自动删除触发消息和机器人回复）")
@Component
public class PingCommandHandler implements BotCommandHandler {

    private final Messenger messenger;

    public PingCommandHandler(Messenger messenger) {
        this.messenger = messenger;
    }

    @Override
    public void execute(BotContext context) {
        if (context.message() != null && context.message().getMessageId() != null) {
            Integer replyMessageId = messenger.sendReplyTextAndReturnMessageId(
                    context.chatId(),
                    context.message().getMessageId(),
                    "\uD83C\uDFD3"
            );
            messenger.deleteMessageSilently(context.chatId(), context.message().getMessageId());
            messenger.deleteMessageSilently(context.chatId(), replyMessageId);
            return;
        }
        messenger.sendText(context.chatId(), "\uD83C\uDFD3");
    }
}
