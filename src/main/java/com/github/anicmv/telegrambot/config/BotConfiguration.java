package com.github.anicmv.telegrambot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anicmv.telegrambot.dispatcher.processor.UpdateProcessor;
import com.github.anicmv.telegrambot.dispatcher.processor.UpdateProcessorRegistry;
import com.github.anicmv.telegrambot.dispatcher.processor.impl.CallbackQueryProcessor;
import com.github.anicmv.telegrambot.dispatcher.processor.impl.ChosenInlineQueryProcessor;
import com.github.anicmv.telegrambot.dispatcher.processor.impl.InlineQueryProcessor;
import com.github.anicmv.telegrambot.dispatcher.processor.impl.MessageProcessor;
import com.github.anicmv.telegrambot.handler.UpdateHandler;
import com.github.anicmv.telegrambot.handler.callback.CallbackActionHandler;
import com.github.anicmv.telegrambot.handler.callback.CallbackActionRegistry;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.handler.command.BotCommandRegistry;
import com.github.anicmv.telegrambot.handler.inline.chosen.ChosenInlineQueryResultHandler;
import com.github.anicmv.telegrambot.handler.inline.chosen.ChosenInlineQueryResultRegistry;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProvider;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProviderRegistry;
import com.github.anicmv.telegrambot.model.UpdateType;
import com.github.anicmv.telegrambot.messenger.impl.TelegramMessenger;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 机器人核心 Bean 装配配置类。
 */
@Configuration
@EnableConfigurationProperties({BotProperties.class, MafProperties.class})
public class BotConfiguration {

    @Bean(name = {"botUpdateExecutor", "applicationTaskExecutor"})
    ThreadPoolTaskExecutor botUpdateExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        int poolSize = Math.max(4, processors);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(poolSize * 200);
        executor.setThreadNamePrefix("bot-update-");
        executor.setAllowCoreThreadTimeOut(false);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    ThreadPoolTaskExecutor botBackgroundExecutor() {
        int poolSize = 2;
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("bot-background-");
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    ThreadPoolTaskScheduler botScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("bot-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    OkHttpClient telegramOkHttpClient(BotProperties properties) {
        BotProperties.Network network = properties.getNetwork();
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(network.getConnectTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(network.getReadTimeoutSeconds()))
                .writeTimeout(Duration.ofSeconds(network.getWriteTimeoutSeconds()));
        return builder.build();
    }

    /**
     * Spring Boot 4 自动配置只提供 Jackson 3（tools.jackson）的 ObjectMapper，
     * 项目内 telegrambots 等依赖仍使用 Jackson 2，这里显式声明一个共享 bean。
     */
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    TelegramBotsLongPollingApplication telegramBotsLongPollingApplication(OkHttpClient telegramOkHttpClient) {
        return new TelegramBotsLongPollingApplication(ObjectMapper::new, () -> telegramOkHttpClient);
    }

    @Bean
    TelegramClient telegramClient(BotProperties properties, OkHttpClient telegramOkHttpClient) {
        return new OkHttpTelegramClient(telegramOkHttpClient, properties.getToken());
    }

    @Bean
    TelegramMessenger messenger(TelegramClient telegramClient,
                                ApplicationEventPublisher eventPublisher,
                                BotProperties properties) {
        return new TelegramMessenger(telegramClient, eventPublisher, properties.getToken());
    }

    @Bean
    BotCommandRegistry botCommandRegistry(List<BotCommandHandler> handlers) {
        return new BotCommandRegistry(handlers);
    }

    @Bean
    InlineQueryResultProviderRegistry inlineQueryResultProviderRegistry(List<InlineQueryResultProvider> providers) {
        return new InlineQueryResultProviderRegistry(providers);
    }

    @Bean
    CallbackActionRegistry callbackActionRegistry(List<CallbackActionHandler> handlers) {
        return new CallbackActionRegistry(handlers);
    }

    @Bean
    ChosenInlineQueryResultRegistry chosenInlineQueryResultRegistry(List<ChosenInlineQueryResultHandler> handlers) {
        return new ChosenInlineQueryResultRegistry(handlers);
    }

    @Bean
    UpdateProcessor messageProcessor(List<UpdateHandler> handlers) {
        return new MessageProcessor(filterHandlers(handlers, UpdateType.MESSAGE));
    }

    @Bean
    UpdateProcessor callbackQueryProcessor(List<UpdateHandler> handlers) {
        return new CallbackQueryProcessor(filterHandlers(handlers, UpdateType.CALLBACK_QUERY));
    }

    @Bean
    UpdateProcessor inlineQueryProcessor(List<UpdateHandler> handlers) {
        return new InlineQueryProcessor(filterHandlers(handlers, UpdateType.INLINE_QUERY));
    }

    @Bean
    UpdateProcessor chosenInlineQueryProcessor(List<UpdateHandler> handlers) {
        return new ChosenInlineQueryProcessor(filterHandlers(handlers, UpdateType.CHOSEN_INLINE_QUERY));
    }

    @Bean
    UpdateProcessorRegistry updateProcessorRegistry(List<UpdateProcessor> processors) {
        return new UpdateProcessorRegistry(processors);
    }

    private List<UpdateHandler> filterHandlers(List<UpdateHandler> handlers, UpdateType updateType) {
        return handlers.stream()
                .filter(handler -> {
                    EnumSet<UpdateType> supportedUpdateTypes = handler.supportedUpdateTypes();
                    return supportedUpdateTypes != null && supportedUpdateTypes.contains(updateType);
                })
                .toList();
    }
}
