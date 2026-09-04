package com.github.anicmv.telegrambot.listener;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.entity.ChatMessageEntity;
import com.github.anicmv.telegrambot.event.GroupMessageReceivedEvent;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.handler.command.BotCommandRegistry;
import com.github.anicmv.telegrambot.listener.filter.BotMessageFilter;
import com.github.anicmv.telegrambot.listener.filter.CommandMessageFilter;
import com.github.anicmv.telegrambot.listener.filter.ForwardedMessageFilter;
import com.github.anicmv.telegrambot.listener.filter.GroupChatFilter;
import com.github.anicmv.telegrambot.listener.filter.GroupMessageFilter;
import com.github.anicmv.telegrambot.listener.filter.RecordConfigFilter;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.repository.ChatMessageRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GroupMessageRecordListenerTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private BotProperties properties;
    private TaskExecutor executor;

    @BeforeEach
    void setUp() {
        properties = new BotProperties();
        executor = Runnable::run;
    }

    @Test
    void shouldNotRecordWhenDisabled() {
        properties.getProfile().setRecordEnabled(false);

        newListener().onGroupMessage(event(-100L, "supergroup"));

        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void shouldNotRecordPrivateChat() {
        enableRecording(-100L);

        newListener().onGroupMessage(event(-100L, "private"));

        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void shouldNotRecordWhenGroupNotWhitelisted() {
        enableRecording(-999L);

        newListener().onGroupMessage(event(-100L, "supergroup"));

        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void shouldNotRecordBotMessage() {
        enableRecording(-100L);

        newListener().onGroupMessage(botEvent(-100L, "supergroup"));

        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void shouldNotRecordForwardedMessage() {
        enableRecording(-100L);

        newListener().onGroupMessage(forwardedEvent(-100L, "supergroup"));

        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void shouldNotRecordBotCommandMessage() {
        enableRecording(-100L);

        newListener().onGroupMessage(commandEvent(-100L, "supergroup", "/ping"));

        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void shouldRecordUnregisteredSlashText() {
        enableRecording(-100L);

        newListener().onGroupMessage(commandEvent(-100L, "supergroup", "/not_a_command"));

        verify(chatMessageRepository).insert(any());
    }

    @Test
    void shouldRecordWhitelistedGroupMessage() {
        enableRecording(-100L);

        newListener().onGroupMessage(event(-100L, "supergroup"));

        ArgumentCaptor<ChatMessageEntity> captor = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(chatMessageRepository).insert(captor.capture());
        ChatMessageEntity entity = captor.getValue();
        assertEquals(-100L, entity.getChatId());
        assertEquals(999L, entity.getTelegramUserId());
        assertEquals("tester", entity.getUsername());
        assertEquals("hello", entity.getContent());
        assertEquals("text", entity.getMessageType());
        assertEquals(42L, entity.getTelegramMessageId());
        assertEquals(LocalDateTime.of(2026, 9, 3, 12, 0), entity.getSentAt());
    }

    @Test
    void repositoryFailureShouldNotPropagate() {
        enableRecording(-100L);
        doThrow(new RuntimeException("db down")).when(chatMessageRepository).insert(any());

        assertDoesNotThrow(() -> newListener().onGroupMessage(event(-100L, "supergroup")));
    }

    @Test
    void rejectedExecutionShouldNotPropagate() {
        enableRecording(-100L);
        TaskExecutor rejecting = task -> {
            throw new RejectedExecutionException("pool full");
        };
        GroupMessageRecordListener listener = newListener(rejecting);

        assertDoesNotThrow(() -> listener.onGroupMessage(event(-100L, "supergroup")));
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void nullEventShouldBeIgnored() {
        newListener().onGroupMessage(null);

        verifyNoInteractions(chatMessageRepository);
    }

    private GroupMessageRecordListener newListener() {
        return newListener(executor);
    }

    private GroupMessageRecordListener newListener(TaskExecutor taskExecutor) {
        List<GroupMessageFilter> filters = List.of(new GroupChatFilter(), new BotMessageFilter(),
                new RecordConfigFilter(properties), new ForwardedMessageFilter(),
                new CommandMessageFilter(new BotCommandRegistry(List.of(new PingTestHandler()))));
        return new GroupMessageRecordListener(chatMessageRepository, taskExecutor, filters);
    }

    private GroupMessageReceivedEvent commandEvent(long chatId, String chatType, String text) {
        return new GroupMessageReceivedEvent(chatId, chatType, 999L, "tester", "Test", false, false,
                "text", null, null, text, 42L, LocalDateTime.of(2026, 9, 3, 12, 0));
    }

    @BotCommand("/ping")
    private static class PingTestHandler implements BotCommandHandler {
        @Override
        public void execute(BotContext context) {
        }
    }

    private void enableRecording(long... groupIds) {
        properties.getProfile().setRecordEnabled(true);
        for (long groupId : groupIds) {
            properties.getProfile().getRecordGroupIds().add(groupId);
        }
    }

    private GroupMessageReceivedEvent event(long chatId, String chatType) {
        return new GroupMessageReceivedEvent(chatId, chatType, 999L, "tester", "Test", false, false,
                "text", null, null, "hello", 42L, LocalDateTime.of(2026, 9, 3, 12, 0));
    }

    private GroupMessageReceivedEvent botEvent(long chatId, String chatType) {
        return new GroupMessageReceivedEvent(chatId, chatType, 999L, "tester", "Test", true, false,
                "text", null, null, "hello", 42L, LocalDateTime.of(2026, 9, 3, 12, 0));
    }

    private GroupMessageReceivedEvent forwardedEvent(long chatId, String chatType) {
        return new GroupMessageReceivedEvent(chatId, chatType, 999L, "tester", "Test", false, true,
                "text", null, null, "hello", 42L, LocalDateTime.of(2026, 9, 3, 12, 0));
    }
}
