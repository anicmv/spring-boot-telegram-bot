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
            return unknown(null);
        }
        if (update.hasMessage()) {
            Message message = update.getMessage();
            return new BotContext(update, UpdateType.MESSAGE,
                    chatId(message.getChat()), userId(message.getFrom()),
                    message.getText(), message, null, null, null);
        }
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            var message = callbackQuery.getMessage();
            return new BotContext(update, UpdateType.CALLBACK_QUERY,
                    chatId(message != null ? message.getChat() : null), userId(callbackQuery.getFrom()),
                    callbackQuery.getData(), null, callbackQuery, null, null);
        }
        if (update.hasInlineQuery()) {
            InlineQuery inlineQuery = update.getInlineQuery();
            return new BotContext(update, UpdateType.INLINE_QUERY,
                    null, userId(inlineQuery.getFrom()),
                    inlineQuery.getQuery(), null, null, inlineQuery, null);
        }
        if (update.hasChosenInlineQuery()) {
            ChosenInlineQuery chosenInlineQuery = update.getChosenInlineQuery();
            return new BotContext(update, UpdateType.CHOSEN_INLINE_QUERY,
                    null, userId(chosenInlineQuery.getFrom()),
                    chosenInlineQuery.getQuery(), null, null, null, chosenInlineQuery);
        }
        return unknown(update);
    }

    private static BotContext unknown(Update update) {
        return new BotContext(update, UpdateType.UNKNOWN, null, null, null, null, null, null, null);
    }

    private static Long chatId(Chat chat) {
        return chat == null ? null : chat.getId();
    }

    private static Long userId(User from) {
        return from == null ? null : from.getId();
    }
}
