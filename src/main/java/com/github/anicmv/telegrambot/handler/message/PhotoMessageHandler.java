package com.github.anicmv.telegrambot.handler.message;

import com.github.anicmv.telegrambot.handler.HandlerResult;
import com.github.anicmv.telegrambot.handler.UpdateHandler;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import java.util.EnumSet;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 图片消息处理器，预留图片业务扩展点。
 */
@Order(20)
@Log4j2
@Component
public class PhotoMessageHandler implements UpdateHandler {

    private final Messenger messenger;

    public PhotoMessageHandler(Messenger messenger) {
        this.messenger = messenger;
    }

    @Override
    public EnumSet<UpdateType> supportedUpdateTypes() {
        return EnumSet.of(UpdateType.MESSAGE);
    }

    @Override
    public boolean supports(BotContext context) {
        return context.updateType() == UpdateType.MESSAGE
                && context.message() != null
                && context.message().hasPhoto();
    }

    @Override
    public HandlerResult handle(BotContext context) {
        log.debug("收到图片了，后续可在这里接入OCR/审核/识图流程 chatid:{}, photo:{}", context.chatId(), context.message().getPhoto());
        return HandlerResult.STOP;
    }

}
