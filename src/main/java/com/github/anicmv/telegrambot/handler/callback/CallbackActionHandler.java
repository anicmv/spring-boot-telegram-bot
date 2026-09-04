package com.github.anicmv.telegrambot.handler.callback;

import com.github.anicmv.telegrambot.annotation.BotCallback;
import com.github.anicmv.telegrambot.model.BotContext;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 按钮回调动作处理接口，动作标识由实现类上的 {@link BotCallback} 注解声明。
 */
public interface CallbackActionHandler {

    void execute(BotContext context, String payload);
}
