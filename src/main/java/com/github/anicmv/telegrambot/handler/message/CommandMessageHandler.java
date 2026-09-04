package com.github.anicmv.telegrambot.handler.message;

import com.github.anicmv.telegrambot.handler.HandlerResult;
import com.github.anicmv.telegrambot.handler.UpdateHandler;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.handler.command.BotCommandRegistry;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import java.util.EnumSet;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 命令消息处理器，识别 /xxx 并转发到命令注册中心。
 */
@Order(10)
@Log4j2
@Component
public class CommandMessageHandler implements UpdateHandler {

    private final BotCommandRegistry commandRegistry;

    public CommandMessageHandler(BotCommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }

    @Override
    public EnumSet<UpdateType> supportedUpdateTypes() {
        return EnumSet.of(UpdateType.MESSAGE);
    }

    @Override
    public boolean supports(BotContext context) {
        return context.updateType() == UpdateType.MESSAGE && context.text() != null && context.text().startsWith("/");
    }

    @Override
    public HandlerResult handle(BotContext context) {
        String commandText = context.text().split("\\s+")[0];
        BotCommandHandler handler = commandRegistry.find(commandText);
        String chatType = context.message() != null && context.message().getChat() != null
                ? String.valueOf(context.message().getChat().getType())
                : "unknown";
        log.info("命令入站: chatId={}, userId={}, chatType={}, rawText={}, commandToken={}",
                context.chatId(), context.userId(), chatType, abbreviate(context.text()), commandText);
        if (handler == null) {
            log.info("命令未命中处理器: commandToken={}", commandText);
            return HandlerResult.CONTINUE;
        }
        log.info("命令命中处理器: commandToken={}, handler={}", commandText, handler.getClass().getSimpleName());
        handler.execute(context);
        return HandlerResult.STOP;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 117) + "...";
    }

}
