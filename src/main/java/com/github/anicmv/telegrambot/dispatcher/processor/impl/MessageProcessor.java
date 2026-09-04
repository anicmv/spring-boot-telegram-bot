package com.github.anicmv.telegrambot.dispatcher.processor.impl;

import com.github.anicmv.telegrambot.dispatcher.processor.UpdateProcessor;
import com.github.anicmv.telegrambot.handler.UpdateHandler;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import java.util.List;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 普通消息处理器，处理 message 类型更新。
 */
public class MessageProcessor extends AbstractChainProcessor implements UpdateProcessor {

    public MessageProcessor(List<UpdateHandler> handlers) {
        super(handlers);
    }

    @Override
    public UpdateType supportType() {
        return UpdateType.MESSAGE;
    }

    @Override
    public void handle(BotContext context) {
        process(context);
    }
}
