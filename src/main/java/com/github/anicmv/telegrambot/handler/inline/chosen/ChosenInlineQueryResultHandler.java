package com.github.anicmv.telegrambot.handler.inline.chosen;

import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.model.BotContext;

/**
 * @author anicmv
 * @date 2026/9/3
 * @description 已选内联结果处理接口，路由的 resultId 由实现类上的 {@link BotInline} 注解声明。
 */
public interface ChosenInlineQueryResultHandler {

    void execute(BotContext context);
}
