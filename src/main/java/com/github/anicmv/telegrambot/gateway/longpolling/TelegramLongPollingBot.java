package com.github.anicmv.telegrambot.gateway.longpolling;

import com.github.anicmv.telegrambot.dispatcher.UpdateDispatcher;
import com.github.anicmv.telegrambot.config.BotProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;

import java.util.concurrent.RejectedExecutionException;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description Long Polling 入口，接收 Telegram 更新并交给应用路由器。
 * 入口从 long polling 开始收消息：
 * TelegramLongPollingBot.java
 * getUpdatesConsumer().consume(Update) -> 调 updateRouter.route(update)。
 * 然后是这条主链：
 * UpdateDispatcher.java
 * 把 Update 转成 BotContext，按 UpdateType 找处理器（Factory）。
 * UpdateProcessorRegistry.java
 * 返回对应处理器：
 * •
 * MESSAGE -> MessageProcessor
 * •
 * CALLBACK_QUERY -> CallbackQueryProcessor
 * •
 * INLINE_QUERY -> InlineQueryProcessor
 * •
 * CHOSEN_INLINE_QUERY -> ChosenInlineQueryProcessor
 * 处理器内部走责任链（按 @Order）：
 * •
 * 消息链：命令/图片/文本等 UpdateHandler
 * •
 * 回调链：CallbackQueryUpdateHandler 解析 action:payload，再进 CallbackActionRegistry 找具体 CallbackActionHandler
 * •
 * inline链：InlineQueryUpdateHandler 调 InlineQueryResultProviderRegistry 收集结果并应答
 * •
 * chosen-inline链：ChosenInlineQueryUpdateHandler 按 resultId 分发，再进 ChosenInlineQueryResultRegistry 找具体 ChosenInlineQueryResultHandler
 * 最终发送回 Telegram 都经过信使：
 * TelegramMessenger.java
 * 这里封装了 sendText/sendPhoto/answerCallback/answerInline/editInline...，底层调用 TelegramClient.execute(...)。
 * 一句话：
 * Telegram -> LongPollingBot consumer -> UpdateDispatcher -> Processor -> Handler/Provider -> Messenger -> Telegram API。
 */
@Component
@Log4j2
public class TelegramLongPollingBot implements SpringLongPollingBot {

    private final BotProperties properties;
    private final UpdateDispatcher updateDispatcher;
    private final TaskExecutor botUpdateExecutor;

    public TelegramLongPollingBot(BotProperties properties,
                                  UpdateDispatcher updateDispatcher,
                                  @Qualifier("botUpdateExecutor") TaskExecutor botUpdateExecutor) {
        this.properties = properties;
        this.updateDispatcher = updateDispatcher;
        this.botUpdateExecutor = botUpdateExecutor;
    }

    @Override
    public String getBotToken() {
        return properties.getToken();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return updates -> updates.forEach(update -> {
            try {
                botUpdateExecutor.execute(() -> routeSafely(update));
            } catch (RejectedExecutionException exception) {
                log.error("Rejected telegram update: updateId={}", update.getUpdateId(), exception);
                routeSafely(update);
            }
        });
    }

    private void routeSafely(org.telegram.telegrambots.meta.api.objects.Update update) {
        try {
            updateDispatcher.route(update);
        } catch (Exception exception) {
            log.error("Failed to process telegram update: updateId={}", update.getUpdateId(), exception);
        }
    }
}
