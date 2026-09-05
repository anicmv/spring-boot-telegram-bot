package com.github.anicmv.telegrambot.handler.callback.impl;

import com.github.anicmv.telegrambot.annotation.BotCallback;
import com.github.anicmv.telegrambot.handler.callback.CallbackActionHandler;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/5/1 15:35
 * @description 占位按钮回调处理器。
 */
@BotCallback(BotConstant.CALLBACK_ACTION_NOOP)
@Component
public class NoopCallbackActionHandler implements CallbackActionHandler {

    private final Messenger messenger;

    public NoopCallbackActionHandler(Messenger messenger) {
        this.messenger = messenger;
    }

    @Override
    public void execute(BotContext context, String payload) {
        if (context.callbackQuery() == null) {
            return;
        }
        messenger.answerCallback(context.callbackQuery().getId(), "AI 正在生成，请稍候");
    }
}
