package com.github.anicmv.telegrambot.handler.inline.provider.impl;

import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProvider;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.utils.ShadiaoCopywritingUtil;
import com.github.anicmv.telegrambot.model.BotContext;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultPhoto;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 彩虹屁 inline 结果提供器。
 */
@BotInline(BotConstant.INLINE_ID_CHP)
@Component
public class ChpInlineQueryResultProvider implements InlineQueryResultProvider {

    @Override
    public boolean supports(BotContext context) {
        String query = context == null ? null : context.text();
        if (query == null || query.isBlank()) {
            return false;
        }
        return query.toLowerCase().contains("chp");
    }

    @Override
    public InlineQueryResult createResult(BotContext context) {
        String text = ShadiaoCopywritingUtil.fetchText(BotConstant.API_CHP, "今天的彩虹屁暂时缺货。");
        InputTextMessageContent content = InputTextMessageContent.builder().messageText(text).build();
        return InlineQueryResultPhoto.builder()
                .id(sortId())
                .photoUrl("https://jpg.moe/i/4gx8j7rz.jpeg")
                .thumbnailUrl("https://jpg.moe/i/4gx8j7rz.jpeg")
                .title("彩虹屁")
                .inputMessageContent(content)
                .build();
    }
}
