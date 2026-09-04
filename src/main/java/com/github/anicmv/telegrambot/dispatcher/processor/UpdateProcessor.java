package com.github.anicmv.telegrambot.dispatcher.processor;

import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description Update 类型处理器接口。
 */
public interface UpdateProcessor {

    UpdateType supportType();

    void handle(BotContext context);
}
