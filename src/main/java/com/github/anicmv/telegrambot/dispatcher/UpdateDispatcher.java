package com.github.anicmv.telegrambot.dispatcher;

import com.github.anicmv.telegrambot.dispatcher.processor.UpdateProcessor;
import com.github.anicmv.telegrambot.dispatcher.processor.UpdateProcessorRegistry;
import com.github.anicmv.telegrambot.event.MessageReceivedEvent;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description Update 总路由，根据类型选择对应处理器。
 */
@Log4j2
@Component
public class UpdateDispatcher {

    private final UpdateProcessorRegistry processorRegistry;
    private final ApplicationEventPublisher eventPublisher;

    public UpdateDispatcher(UpdateProcessorRegistry processorRegistry, ApplicationEventPublisher eventPublisher) {
        this.processorRegistry = processorRegistry;
        this.eventPublisher = eventPublisher;
    }

    public void route(Update update) {
        BotContext context = BotContext.from(update);
        logChat(context);
        if (context.updateType() == UpdateType.MESSAGE) {
            publishMessageReceivedEvent(context);
        }
        UpdateProcessor processor = processorRegistry.get(context.updateType());
        if (processor != null) {
            processor.handle(context);
        }
    }

    /**
     * 打印请求来源日志：群聊 chatId 为负数，附带群名称便于定位。
     */
    private void logChat(BotContext context) {
        if (context.updateType() != UpdateType.MESSAGE || context.message() == null) {
            return;
        }
        Chat chat = context.message().getChat();
        if (chat == null) {
            return;
        }
        if (chat.isGroupChat() || chat.isSuperGroupChat()) {
            log.info("群聊使用 bot: chatId={}, 群名称={}, userId={}",
                    chat.getId(), chat.getTitle(), context.userId());
        } else {
            log.info("私聊使用 bot: chatId={}, userId={}", chat.getId(), context.userId());
        }
    }

    /**
     * 在责任链路由之前发布消息接收事件，消息记录不受链上短路或异常影响。
     */
    private void publishMessageReceivedEvent(BotContext context) {
        try {
            MessageReceivedEvent.from(context).ifPresent(eventPublisher::publishEvent);
        } catch (Exception e) {
            log.error("发布 MessageReceivedEvent 失败: chatId={}", context.chatId(), e);
        }
    }
}
