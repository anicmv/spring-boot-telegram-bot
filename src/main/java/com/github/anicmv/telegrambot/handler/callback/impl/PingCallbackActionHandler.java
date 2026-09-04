package com.github.anicmv.telegrambot.handler.callback.impl;

import com.github.anicmv.telegrambot.annotation.BotCallback;
import com.github.anicmv.telegrambot.handler.callback.CallbackActionHandler;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 示例回调动作，处理 PING 按钮事件。
 */
@BotCallback(BotConstant.CALLBACK_ACTION_PING)
@Component
public class PingCallbackActionHandler implements CallbackActionHandler {

    private final Messenger messenger;

    public PingCallbackActionHandler(Messenger messenger) {
        this.messenger = messenger;
    }

    @Override
    public void execute(BotContext context, String payload) {
        messenger.answerCallback(context.callbackQuery().getId(), "PONG " + payload);
        // messenger.sendText(context.chatId(), "按钮回调已处理，payload=" + payload);
    }
}
