package com.github.anicmv.telegrambot.dispatcher.processor.impl;

import com.github.anicmv.telegrambot.dispatcher.processor.UpdateProcessor;
import com.github.anicmv.telegrambot.handler.UpdateHandler;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import java.util.List;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 内联查询处理器，处理 inline query 类型更新。
 */
public class InlineQueryProcessor extends AbstractChainProcessor implements UpdateProcessor {

    public InlineQueryProcessor(List<UpdateHandler> handlers) {
        super(handlers);
    }

    @Override
    public UpdateType supportType() {
        return UpdateType.INLINE_QUERY;
    }

    @Override
    public void handle(BotContext context) {
        process(context);
    }
}
