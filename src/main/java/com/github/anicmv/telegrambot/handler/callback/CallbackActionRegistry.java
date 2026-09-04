package com.github.anicmv.telegrambot.handler.callback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.anicmv.telegrambot.annotation.BotCallback;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 回调动作注册中心，按 action 查找处理器。动作标识读取自处理器类上的 {@link BotCallback} 注解。
 */
public class CallbackActionRegistry {

    private final Map<String, CallbackActionHandler> handlers = new HashMap<>();

    public CallbackActionRegistry(List<CallbackActionHandler> handlers) {
        for (CallbackActionHandler handler : handlers) {
            String action = resolveAction(handler);
            CallbackActionHandler previous = this.handlers.putIfAbsent(action, handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate callback action handler: " + action);
            }
        }
    }

    public CallbackActionHandler find(String action) {
        CallbackActionHandler direct = handlers.get(action);
        if (direct != null) {
            return direct;
        }
        if (action == null) {
            return null;
        }
        for (Map.Entry<String, CallbackActionHandler> entry : handlers.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.endsWith("*")) {
                String prefix = key.substring(0, key.length() - 1);
                if (action.startsWith(prefix)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static String resolveAction(CallbackActionHandler handler) {
        BotCallback annotation = AnnotationUtils.findAnnotation(handler.getClass(), BotCallback.class);
        if (annotation == null || annotation.value() == null || annotation.value().isBlank()) {
            throw new IllegalStateException("Missing @BotCallback annotation on handler: " + handler.getClass().getName());
        }
        return annotation.value();
    }
}
