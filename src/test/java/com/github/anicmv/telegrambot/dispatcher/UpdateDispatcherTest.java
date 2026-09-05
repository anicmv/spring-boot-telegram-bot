package com.github.anicmv.telegrambot.dispatcher;

import com.github.anicmv.telegrambot.dispatcher.processor.UpdateProcessor;
import com.github.anicmv.telegrambot.dispatcher.processor.UpdateProcessorRegistry;
import com.github.anicmv.telegrambot.event.MessageReceivedEvent;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.UpdateType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateDispatcherTest {

    @Mock
    private UpdateProcessorRegistry processorRegistry;

    @Mock
    private UpdateProcessor processor;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void messageUpdateShouldPublishEventAndRoute() {
        when(processorRegistry.get(UpdateType.MESSAGE)).thenReturn(processor);

        new UpdateDispatcher(processorRegistry, eventPublisher).route(textUpdate());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertInstanceOf(MessageReceivedEvent.class, captor.getValue());
        verify(processor).handle(any(BotContext.class));
    }

    @Test
    void nonMessageUpdateShouldNotPublishEvent() {
        when(processorRegistry.get(UpdateType.UNKNOWN)).thenReturn(null);

        new UpdateDispatcher(processorRegistry, eventPublisher).route(new Update());

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void eventPublishFailureShouldNotBreakRouting() {
        when(processorRegistry.get(UpdateType.MESSAGE)).thenReturn(processor);
        doThrow(new RuntimeException("boom")).when(eventPublisher).publishEvent(any(Object.class));

        UpdateDispatcher dispatcher = new UpdateDispatcher(processorRegistry, eventPublisher);
        assertDoesNotThrow(() -> dispatcher.route(textUpdate()));

        verify(processor).handle(any(BotContext.class));
    }

    private Update textUpdate() {
        Message message = new Message();
        message.setMessageId(42);
        message.setText("大家好");
        message.setChat(Chat.builder().id(-100123L).type("supergroup").build());
        message.setFrom(User.builder().id(999L).isBot(false).userName("tester").firstName("Test").build());
        Update update = new Update();
        update.setMessage(message);
        return update;
    }
}
