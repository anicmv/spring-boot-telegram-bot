package com.github.anicmv.telegrambot.handler.message;

import com.github.anicmv.telegrambot.handler.HandlerResult;
import com.github.anicmv.telegrambot.handler.UpdateHandler;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import java.util.EnumSet;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 文本消息处理器，作为普通文本兜底。
 */
@Order(100)
@Log4j2
@Component
public class TextMessageHandler implements UpdateHandler {

    public TextMessageHandler() {
    }

    @Override
    public EnumSet<UpdateType> supportedUpdateTypes() {
        return EnumSet.of(UpdateType.MESSAGE);
    }

    @Override
    public boolean supports(BotContext context) {
        return context.updateType() == UpdateType.MESSAGE
                && context.message() != null
                && context.message().hasText();
    }

    @Override
    public HandlerResult handle(BotContext context) {
        log.debug("Update handled: userId={} text: {}", context.chatId(), context.text());
        return HandlerResult.STOP;
    }

}
