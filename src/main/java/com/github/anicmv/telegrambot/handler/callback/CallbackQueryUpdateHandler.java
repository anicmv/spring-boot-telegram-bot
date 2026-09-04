package com.github.anicmv.telegrambot.handler.callback;

import com.github.anicmv.telegrambot.handler.HandlerResult;
import com.github.anicmv.telegrambot.handler.UpdateHandler;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import java.util.EnumSet;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description CallbackQuery 处理器，解析 action:payload 并分发。
 */
@Order(10)
@Component
public class CallbackQueryUpdateHandler implements UpdateHandler {

    private final CallbackActionRegistry actionRegistry;
    private final Messenger messenger;

    public CallbackQueryUpdateHandler(CallbackActionRegistry actionRegistry, Messenger messenger) {
        this.actionRegistry = actionRegistry;
        this.messenger = messenger;
    }

    @Override
    public EnumSet<UpdateType> supportedUpdateTypes() {
        return EnumSet.of(UpdateType.CALLBACK_QUERY);
    }

    @Override
    public boolean supports(BotContext context) {
        return context.updateType() == UpdateType.CALLBACK_QUERY && context.callbackQuery() != null;
    }

    @Override
    public HandlerResult handle(BotContext context) {
        String data = context.callbackQuery().getData();
        if (data == null || data.isBlank()) {
            return HandlerResult.STOP;
        }
        String[] parts = data.split(":", 2);
        String action = parts[0];
        String payload = parts.length > 1 ? parts[1] : "";
        CallbackActionHandler handler = actionRegistry.find(action);
        if (handler != null) {
            handler.execute(context, payload);
        } else {
            messenger.answerCallback(context.callbackQuery().getId(), "未识别的按钮动作: " + action);
        }
        return HandlerResult.STOP;
    }

}
