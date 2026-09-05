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
 * @description /chp 命令处理器（彩虹屁）。
 */
@BotCommand(value = BotConstant.CMD_CHP, description = "彩虹屁")
@Component
public class ChpCommandHandler implements BotCommandHandler {

    private final Messenger messenger;

    public ChpCommandHandler(Messenger messenger) {
        this.messenger = messenger;
    }

    @Override
    public void execute(BotContext context) {
        String text = ShadiaoCopywritingUtil.fetchText(BotConstant.API_CHP, "获取彩虹屁失败，请稍后重试。");
        Replier.of(context, messenger).text(text);
    }
}
