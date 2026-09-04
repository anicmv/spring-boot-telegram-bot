package com.github.anicmv.telegrambot.handler.command;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 命令注册中心，按命令文本查找处理器。命令文本与描述读取自处理器类上的 {@link BotCommand} 注解。
 */
public class BotCommandRegistry {

    private final Map<String, BotCommandHandler> handlers = new HashMap<>();
    private final Map<String, String> descriptions = new HashMap<>();

    public BotCommandRegistry(List<BotCommandHandler> handlers) {
        for (BotCommandHandler handler : handlers) {
            BotCommand annotation = resolveAnnotation(handler);
            BotCommandHandler previous = this.handlers.putIfAbsent(annotation.value(), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate bot command handler: " + annotation.value());
            }
            this.descriptions.put(annotation.value(), annotation.description());
        }
    }

    public BotCommandHandler find(String command) {
        BotCommandHandler direct = handlers.get(command);
        if (direct != null) {
            return direct;
        }
        if (command == null) {
            return null;
        }
        int atIndex = command.indexOf('@');
        if (atIndex > 0) {
            return handlers.get(command.substring(0, atIndex));
        }
        return null;
    }

    /**
     * 返回描述非空的命令，按命令文本排序，供 /help 聚合展示。
     */
    public List<CommandInfo> describedCommands() {
        return descriptions.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> new CommandInfo(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CommandInfo::command))
                .toList();
    }

    private static BotCommand resolveAnnotation(BotCommandHandler handler) {
        BotCommand annotation = AnnotationUtils.findAnnotation(handler.getClass(), BotCommand.class);
        if (annotation == null || annotation.value() == null || annotation.value().isBlank()) {
            throw new IllegalStateException("Missing @BotCommand annotation on handler: " + handler.getClass().getName());
        }
        return annotation;
    }

    /**
     * @description /help 展示用的命令信息。
     */
    public record CommandInfo(String command, String description) {
    }
}
