package com.github.anicmv.telegrambot.handler.command;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.model.BotContext;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 机器人命令处理接口，命令文本由实现类上的 {@link BotCommand} 注解声明。
 */
public interface BotCommandHandler {

    void execute(BotContext context);
}
