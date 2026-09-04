package com.github.anicmv.telegrambot.annotation;

import com.github.anicmv.telegrambot.handler.callback.CallbackActionHandler;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author anicmv
 * @date 2026/9/3
 * @description 声明回调动作处理器响应的动作标识（如 {@code "XP_*"}）。
 * 标注在 {@link CallbackActionHandler} 实现类上，注册中心启动时读取该注解构建动作路由，
 * 缺少注解或动作重复时启动即失败。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface BotCallback {

    /**
     * 动作标识，如 {@code "PING"}；支持 {@code "*"} 后缀通配，如 {@code "XP_*"}。
     */
    String value();
}
