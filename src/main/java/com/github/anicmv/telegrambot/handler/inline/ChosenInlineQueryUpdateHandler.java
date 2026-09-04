package com.github.anicmv.telegrambot.handler.inline;

import com.github.anicmv.telegrambot.handler.HandlerResult;
import com.github.anicmv.telegrambot.handler.UpdateHandler;
import com.github.anicmv.telegrambot.handler.inline.chosen.ChosenInlineQueryResultHandler;
import com.github.anicmv.telegrambot.handler.inline.chosen.ChosenInlineQueryResultRegistry;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import java.util.EnumSet;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/9/3
 * @description ChosenInlineQuery 处理器，按 resultId 分发到具体结果处理器。
 */
@Order(10)
@Log4j2
@Component
public class ChosenInlineQueryUpdateHandler implements UpdateHandler {

    private final ChosenInlineQueryResultRegistry resultRegistry;

    public ChosenInlineQueryUpdateHandler(ChosenInlineQueryResultRegistry resultRegistry) {
        this.resultRegistry = resultRegistry;
    }

    @Override
    public EnumSet<UpdateType> supportedUpdateTypes() {
        return EnumSet.of(UpdateType.CHOSEN_INLINE_QUERY);
    }

    @Override
    public boolean supports(BotContext context) {
        return context.updateType() == UpdateType.CHOSEN_INLINE_QUERY && context.chosenInlineQuery() != null;
    }

    @Override
    public HandlerResult handle(BotContext context) {
        String resultId = context.chosenInlineQuery().getResultId();
        ChosenInlineQueryResultHandler handler = resultRegistry.find(resultId);
        if (handler == null) {
            log.debug("已选内联结果未命中处理器: resultId={}", resultId);
            return HandlerResult.STOP;
        }
        handler.execute(context);
        return HandlerResult.STOP;
    }
}
