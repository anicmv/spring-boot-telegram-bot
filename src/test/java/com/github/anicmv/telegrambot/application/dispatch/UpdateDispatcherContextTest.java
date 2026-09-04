package com.github.anicmv.telegrambot.application.dispatch;

import com.github.anicmv.telegrambot.dispatcher.UpdateDispatcher;
import com.github.anicmv.telegrambot.dispatcher.processor.UpdateProcessorRegistry;
import com.github.anicmv.telegrambot.config.BotConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class UpdateDispatcherContextTest {

    @Test
    void shouldRegisterSingleUpdateDispatcherComponent() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(
                    UpdateProcessorRegistry.class,
                    () -> new UpdateProcessorRegistry(List.of())
            );
            context.register(UpdateDispatcher.class);
            context.refresh();

            assertEquals(List.of("updateDispatcher"), List.copyOf(context.getBeansOfType(UpdateDispatcher.class).keySet()));
            assertSame(context.getBean(UpdateDispatcher.class), context.getBean("updateDispatcher"));
        }
    }

    @Test
    void botConfigurationShouldNotDeclareUpdateDispatcherBean() {
        boolean declaresUpdateDispatcher = List.of(BotConfiguration.class.getDeclaredMethods()).stream()
                .anyMatch(method -> method.isAnnotationPresent(Bean.class)
                        && UpdateDispatcher.class.isAssignableFrom(method.getReturnType()));

        assertFalse(declaresUpdateDispatcher);
    }
}
