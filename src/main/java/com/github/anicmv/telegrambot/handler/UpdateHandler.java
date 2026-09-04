package com.github.anicmv.telegrambot.handler;

import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import java.util.EnumSet;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 统一更新处理器接口。
 */
public interface UpdateHandler {

    default EnumSet<UpdateType> supportedUpdateTypes() {
        return EnumSet.allOf(UpdateType.class);
    }

    boolean supports(BotContext context);

    HandlerResult handle(BotContext context);
}
