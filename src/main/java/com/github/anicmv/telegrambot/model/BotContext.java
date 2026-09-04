package com.github.anicmv.telegrambot.model;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.ChosenInlineQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 统一上下文模型，屏蔽不同 Update 结构差异。
 */
public record BotContext(
        Update update,
        UpdateType updateType,
        Long chatId,
        Long userId,
        String text,
        Message message,
        CallbackQuery callbackQuery,
        InlineQuery inlineQuery,
        ChosenInlineQuery chosenInlineQuery
) {

    public static BotContext from(Update update) {
        if (update == null) {
            return new BotContext(null, UpdateType.UNKNOWN, null, null, null, null, null, null, null);
        }
        if (update.hasMessage()) {
            Message message = update.getMessage();
            Chat chat = message.getChat();
            User from = message.getFrom();
            return new BotContext(
                    update,
                    UpdateType.MESSAGE,
                    chat != null ? chat.getId() : null,
                    from != null ? from.getId() : null,
                    message.getText(),
                    message,
                    null,
                    null,
                    null
            );
        }
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            var message = callbackQuery.getMessage();
            Chat chat = message != null ? message.getChat() : null;
            User from = callbackQuery.getFrom();
            return new BotContext(
                    update,
                    UpdateType.CALLBACK_QUERY,
                    chat != null ? chat.getId() : null,
                    from != null ? from.getId() : null,
                    callbackQuery.getData(),
                    null,
                    callbackQuery,
                    null,
                    null
            );
        }
        if (update.hasInlineQuery()) {
            InlineQuery inlineQuery = update.getInlineQuery();
            User from = inlineQuery.getFrom();
            return new BotContext(
                    update,
                    UpdateType.INLINE_QUERY,
                    null,
                    from.getId(),
                    inlineQuery.getQuery(),
                    null,
                    null,
                    inlineQuery,
                    null
            );
        }
        if (update.hasChosenInlineQuery()) {
            ChosenInlineQuery chosenInlineQuery = update.getChosenInlineQuery();
            User from = chosenInlineQuery.getFrom();
            return new BotContext(
                    update,
                    UpdateType.CHOSEN_INLINE_QUERY,
                    null,
                    from.getId(),
                    chosenInlineQuery.getQuery(),
                    null,
                    null,
                    null,
                    chosenInlineQuery
            );
        }
        return new BotContext(update, UpdateType.UNKNOWN, null, null, null, null, null, null, null);
    }
}
