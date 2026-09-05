package com.github.anicmv.telegrambot.handler.command;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final Set<String> mentionRequiredCommands = new HashSet<>();
    private final String botUsername;

    public BotCommandRegistry(List<BotCommandHandler> handlers, String botUsername) {
        this.botUsername = botUsername;
        for (BotCommandHandler handler : handlers) {
            BotCommand annotation = resolveAnnotation(handler);
            BotCommandHandler previous = this.handlers.putIfAbsent(annotation.value(), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate bot command handler: " + annotation.value());
            }
            this.descriptions.put(annotation.value(), annotation.description());
            if (annotation.groupRequireMention()) {
                this.mentionRequiredCommands.add(annotation.value());
            }
        }
    }

    /**
     * 宽松查找：忽略 {@code @后缀}，任意命令形式都命中。供记录链过滤等场景使用。
     */
    public BotCommandHandler find(String command) {
        return find(command, false);
    }

    /**
     * 查找处理器。{@code mentionRequired} 为 true（群聊）时，
     * 声明了 {@code groupRequireMention} 的命令必须形如 {@code /ai@<botUsername>} 才命中，
     * 裸 {@code /ai} 或其他 {@code @后缀} 均不触发；未声明该标志的命令不受影响。
     */
    public BotCommandHandler find(String command, boolean mentionRequired) {
        if (command == null) {
            return null;
        }
        int atIndex = command.indexOf('@');
        String base = atIndex > 0 ? command.substring(0, atIndex) : command;
        BotCommandHandler handler = handlers.get(base);
        if (handler == null) {
            return null;
        }
        if (mentionRequired && mentionRequiredCommands.contains(base)) {
            return matchesBotUsername(atIndex, command) ? handler : null;
        }
        return handler;
    }

    /**
     * 要求 token 形如 {@code /ai@<botUsername>}，用户名大小写不敏感；
     * botUsername 未配置时降级为仅要求带 {@code @} 后缀，避免命令完全不可用。
     */
    private boolean matchesBotUsername(int atIndex, String command) {
        if (atIndex <= 0) {
            return false;
        }
        if (botUsername == null || botUsername.isBlank()) {
            return true;
        }
        return botUsername.equalsIgnoreCase(command.substring(atIndex + 1));
    }

    /**
     * 返回描述非空的命令，按命令文本排序，供 /help 聚合展示。
     * 群聊需 @ 提及的命令以 {@code /ai@<botUsername>} 形式展示（用户名未配置时保持原样）。
     */
    public List<CommandInfo> describedCommands() {
        return descriptions.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> new CommandInfo(displayCommand(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(CommandInfo::command))
                .toList();
    }

    private String displayCommand(String command) {
        if (mentionRequiredCommands.contains(command) && botUsername != null && !botUsername.isBlank()) {
            return command + "@" + botUsername;
        }
        return command;
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
