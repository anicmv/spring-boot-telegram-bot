package com.github.anicmv.telegrambot.annotation;

import com.github.anicmv.telegrambot.handler.inline.chosen.ChosenInlineQueryResultHandler;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProvider;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author anicmv
 * @date 2026/9/3
 * @description 声明内联结果标识（如 {@code "N"}）。
 * 标注在 {@link InlineQueryResultProvider} 实现类上时，作为该提供器生成结果的排序与结果 id；
 * 标注在 {@link ChosenInlineQueryResultHandler} 实现类上时，作为该处理器路由的 resultId。
 * 缺少注解或标识重复时启动即失败。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface BotInline {

    /**
     * 内联结果标识，如 {@code "N"}。
     */
    String value();
}
