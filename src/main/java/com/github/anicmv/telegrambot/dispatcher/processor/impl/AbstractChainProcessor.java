package com.github.anicmv.telegrambot.dispatcher.processor.impl;

import com.github.anicmv.telegrambot.handler.HandlerResult;
import com.github.anicmv.telegrambot.handler.UpdateHandler;
import com.github.anicmv.telegrambot.model.BotContext;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 责任链处理器基类，按顺序执行处理器并支持短路。
 */
public abstract class AbstractChainProcessor {

    private final List<UpdateHandler> handlers;

    protected AbstractChainProcessor(List<UpdateHandler> handlers) {
        List<UpdateHandler> sortedHandlers = new ArrayList<>(handlers);
        sortedHandlers.sort(AnnotationAwareOrderComparator.INSTANCE);
        this.handlers = sortedHandlers;
    }

    protected void process(BotContext context) {
        for (UpdateHandler handler : handlers) {
            if (!handler.supports(context)) {
                continue;
            }
            HandlerResult result = handler.handle(context);
            if (result == HandlerResult.STOP) {
                return;
            }
        }
    }
}
