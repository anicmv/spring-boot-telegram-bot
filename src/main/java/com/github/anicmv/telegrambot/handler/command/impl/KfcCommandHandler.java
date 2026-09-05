package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.utils.ShadiaoCopywritingUtil;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.Replier;
import com.github.anicmv.telegrambot.model.BotContext;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description /kfc 命令处理器。
 */
@BotCommand(value = BotConstant.CMD_KFC, description = "KFC 文案")
@Component
public class KfcCommandHandler implements BotCommandHandler {

    private final Messenger messenger;

    public KfcCommandHandler(Messenger messenger) {
        this.messenger = messenger;
    }

    @Override
    public void execute(BotContext context) {
        String text = ShadiaoCopywritingUtil.fetchText(BotConstant.API_KFC, "获取疯狂星期四文案失败，请稍后重试。");
        Replier.of(context, messenger).text(text);
    }
}
