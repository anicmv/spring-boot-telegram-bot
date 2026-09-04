package com.github.anicmv.telegrambot.dispatcher.processor.impl;

import com.github.anicmv.telegrambot.dispatcher.processor.UpdateProcessor;
import com.github.anicmv.telegrambot.handler.UpdateHandler;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import java.util.List;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 回调查询处理器，处理按钮 callback 类型更新。
 */
public class CallbackQueryProcessor extends AbstractChainProcessor implements UpdateProcessor {

    public CallbackQueryProcessor(List<UpdateHandler> handlers) {
        super(handlers);
    }

    @Override
    public UpdateType supportType() {
        return UpdateType.CALLBACK_QUERY;
    }

    @Override
    public void handle(BotContext context) {
        process(context);
    }
}
