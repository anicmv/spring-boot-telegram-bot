package com.github.anicmv.telegrambot.handler.inline.provider;

import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.model.BotContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;

import java.util.List;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description Inline 查询结果提供器接口，结果标识由实现类上的 {@link BotInline} 注解声明。
 */
public interface InlineQueryResultProvider {

    default String sortId() {
        BotInline annotation = AnnotationUtils.findAnnotation(getClass(), BotInline.class);
        if (annotation == null || annotation.value() == null || annotation.value().isBlank()) {
            throw new IllegalStateException("Missing @InlineResultId annotation on provider: " + getClass().getName());
        }
        return annotation.value();
    }

    default boolean supports(BotContext context) {
        String query = context == null ? null : context.text();
        return query == null || query.isBlank();
    }

    default List<InlineQueryResult> createResults(BotContext context) {
        return List.of(createResult(context));
    }

    InlineQueryResult createResult(BotContext context);
}
