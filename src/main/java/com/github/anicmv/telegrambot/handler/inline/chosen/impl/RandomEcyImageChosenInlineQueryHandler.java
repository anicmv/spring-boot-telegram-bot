package com.github.anicmv.telegrambot.handler.inline.chosen.impl;

import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.handler.inline.chosen.ChosenInlineQueryResultHandler;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.utils.BotUtil;
import com.github.anicmv.telegrambot.utils.HttpUtil;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 已选择内联结果处理器，随机替换二次元图片。
 */
@BotInline(BotConstant.INLINE_ID_RANDOM_ECY)
@Component
public class RandomEcyImageChosenInlineQueryHandler implements ChosenInlineQueryResultHandler {

    private static final List<String> IMAGE_APIS = List.of(
            BotConstant.API_RANDOM_ECY_1,
            BotConstant.API_RANDOM_ECY_2
    );

    private final Messenger messenger;

    public RandomEcyImageChosenInlineQueryHandler(Messenger messenger) {
        this.messenger = messenger;
    }

    @Override
    public void execute(BotContext context) {
        String api = BotUtil.randomOne(IMAGE_APIS);
        String imageUrl = HttpUtil.redirectUrl(api, Map.of(BotConstant.HEADER_USER_AGENT, BotConstant.USER_AGENT));
        if (imageUrl != null && !imageUrl.isBlank()) {
            messenger.editInlineMessagePhoto(
                    context.chosenInlineQuery().getInlineMessageId(),
                    imageUrl,
                    "",
                    null
            );
        }
    }
}
