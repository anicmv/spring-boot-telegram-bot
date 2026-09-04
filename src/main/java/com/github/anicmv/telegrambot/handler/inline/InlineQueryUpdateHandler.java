package com.github.anicmv.telegrambot.handler.inline;

import com.github.anicmv.telegrambot.handler.HandlerResult;
import com.github.anicmv.telegrambot.handler.UpdateHandler;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProviderRegistry;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import java.util.EnumSet;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description InlineQuery 处理器，构造并返回内联结果。
 */
@Order(10)
@Component
@Log4j2
public class InlineQueryUpdateHandler implements UpdateHandler {

    private static final int PAGE_SIZE = 12;

    private final Messenger messenger;
    private final InlineQueryResultProviderRegistry providerRegistry;

    public InlineQueryUpdateHandler(Messenger messenger, InlineQueryResultProviderRegistry providerRegistry) {
        this.messenger = messenger;
        this.providerRegistry = providerRegistry;
    }

    @Override
    public EnumSet<UpdateType> supportedUpdateTypes() {
        return EnumSet.of(UpdateType.INLINE_QUERY);
    }

    @Override
    public boolean supports(BotContext context) {
        return context.updateType() == UpdateType.INLINE_QUERY && context.inlineQuery() != null;
    }

    @Override
    public HandlerResult handle(BotContext context) {
        boolean dbMode = isDbMode(context);
        int page = parsePage(context.inlineQuery().getOffset());
        List<InlineQueryResult> allResults = providerRegistry.createAll(context);
        List<InlineQueryResult> pageResults;
        String nextOffset;
        if (dbMode) {
            pageResults = allResults;
            nextOffset = "";
        } else {
            int fromIndex = Math.min(page * PAGE_SIZE, allResults.size());
            int toIndex = Math.min(fromIndex + PAGE_SIZE, allResults.size());
            pageResults = allResults.subList(fromIndex, toIndex);
            nextOffset = toIndex < allResults.size() ? String.valueOf(page + 1) : "";
        }

        log.info("inline pagination queryId={}, query='{}', offset={}, page={}, returned={}, total={}, nextOffset={}",
                context.inlineQuery().getId(),
                context.inlineQuery().getQuery(),
                context.inlineQuery().getOffset(),
                page,
                pageResults.size(),
                allResults.size(),
                nextOffset);
        messenger.answerInline(context.inlineQuery().getId(), pageResults, nextOffset);
        return HandlerResult.STOP;
    }

    private boolean isDbMode(BotContext context) {
        String query = context == null ? null : context.text();
        return query != null && query.trim().toLowerCase().startsWith("db");
    }

    private int parsePage(String offset) {
        if (offset == null || offset.isBlank()) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(offset);
            return Math.max(parsed, 0);
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

}
