package com.github.anicmv.telegrambot.handler.inline.chosen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.anicmv.telegrambot.annotation.BotInline;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * @author anicmv
 * @date 2026/9/3
 * @description 已选内联结果注册中心，按 resultId 查找处理器。
 * resultId 读取自处理器类上的 {@link BotInline} 注解。
 */
public class ChosenInlineQueryResultRegistry {

    private final Map<String, ChosenInlineQueryResultHandler> handlers = new HashMap<>();

    public ChosenInlineQueryResultRegistry(List<ChosenInlineQueryResultHandler> handlers) {
        for (ChosenInlineQueryResultHandler handler : handlers) {
            String resultId = resolveResultId(handler);
            ChosenInlineQueryResultHandler previous = this.handlers.putIfAbsent(resultId, handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate chosen inline result handler: " + resultId);
            }
        }
    }

    public ChosenInlineQueryResultHandler find(String resultId) {
        if (resultId == null) {
            return null;
        }
        return handlers.get(resultId);
    }

    private static String resolveResultId(ChosenInlineQueryResultHandler handler) {
        BotInline annotation = AnnotationUtils.findAnnotation(handler.getClass(), BotInline.class);
        if (annotation == null || annotation.value() == null || annotation.value().isBlank()) {
            throw new IllegalStateException("Missing @InlineResultId annotation on handler: " + handler.getClass().getName());
        }
        return annotation.value();
    }
}
