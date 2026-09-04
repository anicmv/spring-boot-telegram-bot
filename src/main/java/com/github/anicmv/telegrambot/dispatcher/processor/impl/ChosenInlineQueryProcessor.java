package com.github.anicmv.telegrambot.dispatcher.processor.impl;

import com.github.anicmv.telegrambot.dispatcher.processor.UpdateProcessor;
import com.github.anicmv.telegrambot.handler.UpdateHandler;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import java.util.List;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 已选择内联结果处理器，处理 chosen inline query 类型更新。
 */
public class ChosenInlineQueryProcessor extends AbstractChainProcessor implements UpdateProcessor {

    public ChosenInlineQueryProcessor(List<UpdateHandler> handlers) {
        super(handlers);
    }

    @Override
    public UpdateType supportType() {
        return UpdateType.CHOSEN_INLINE_QUERY;
    }

    @Override
    public void handle(BotContext context) {
        process(context);
    }
}
