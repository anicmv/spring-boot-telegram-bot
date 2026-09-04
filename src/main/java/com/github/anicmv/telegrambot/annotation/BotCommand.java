package com.github.anicmv.telegrambot.annotation;

import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author anicmv
 * @date 2026/9/3
 * @description 声明命令处理器响应的命令文本（如 "/ping"）。
 * 标注在 {@link BotCommandHandler} 实现类上，注册中心启动时读取该注解构建命令路由，
 * 缺少注解或命令重复时启动即失败。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface BotCommand {

    /**
     * 命令文本，如 {@code "/ping"}。
     */
    String value();

    /**
     * 命令描述，非空时展示在 {@code /help} 中，为空表示不展示。
     */
    String description() default "";
}
