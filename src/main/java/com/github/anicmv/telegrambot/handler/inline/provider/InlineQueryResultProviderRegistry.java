package com.github.anicmv.telegrambot.handler.inline.provider;

import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.model.BotContext;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;

import java.util.Comparator;
import java.util.List;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description Inline 查询结果提供器注册中心。
 */
public class InlineQueryResultProviderRegistry {

    private final List<InlineQueryResultProvider> providers;

    public InlineQueryResultProviderRegistry(List<InlineQueryResultProvider> providers) {
        this.providers = providers.stream()
                .sorted(Comparator.comparing(InlineQueryResultProvider::sortId))
                .toList();
    }

    public List<InlineQueryResultProvider> providers() {
        return providers;
    }

    public List<InlineQueryResult> createAll(BotContext context) {
        String query = context == null ? null : context.text();
        boolean dbMode = query != null && query.trim().toLowerCase().startsWith("db");
        if (dbMode) {
            return providers.stream()
                    .filter(provider -> BotConstant.INLINE_ID_DB.equals(provider.sortId()))
                    .filter(provider -> provider.supports(context))
                    .flatMap(provider -> provider.createResults(context).stream())
                    .toList();
        }
        return providers.stream()
                .filter(provider -> provider.supports(context))
                .flatMap(provider -> provider.createResults(context).stream())
                .toList();
    }
}
