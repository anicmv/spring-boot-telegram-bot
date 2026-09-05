package com.github.anicmv.telegrambot.event;

import com.github.anicmv.telegrambot.model.BotContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageReceivedEventTest {

    @Test
    void shouldExtractTextMessageFromSuperGroup() {
        BotContext context = messageContext(-100123L, "supergroup", "今天天气不错");

        MessageReceivedEvent event = MessageReceivedEvent.from(context).orElseThrow();

        assertNotNull(event);
        assertTrue(event.isGroupChat());
        assertEquals(-100123L, event.chatId());
        assertEquals("supergroup", event.chatType());
        assertEquals(999L, event.userId());
        assertEquals("tester", event.username());
        assertEquals("Test User", event.nickname());
        assertEquals("text", event.messageType());
        assertEquals("今天天气不错", event.text());
        assertEquals(42L, event.telegramMessageId());
        assertNotNull(event.sentAt());
    }

    @Test
    void privateChatShouldNotBeGroupChat() {
        BotContext context = messageContext(555L, "private", "你好");

        MessageReceivedEvent event = MessageReceivedEvent.from(context).orElseThrow();

        assertNotNull(event);
        assertFalse(event.isGroupChat());
    }

    @Test
    void basicGroupShouldBeGroupChat() {
        BotContext context = messageContext(-456L, "group", "大家好");

        MessageReceivedEvent event = MessageReceivedEvent.from(context).orElseThrow();

        assertNotNull(event);
        assertTrue(event.isGroupChat());
    }

    @Test
    void photoMessageShouldBeRecordedAsPhotoType() {
        Message message = baseMessage(-100123L, "supergroup");
        message.setText(null);
        message.setPhoto(List.of(new PhotoSize()));

        MessageReceivedEvent event = MessageReceivedEvent.from(BotContext.from(wrap(message))).orElseThrow();

        assertNotNull(event);
        assertEquals("photo", event.messageType());
        assertNull(event.text());
    }

    @Test
    void photoCaptionShouldBeUsedAsText() {
        Message message = baseMessage(-100123L, "supergroup");
        message.setText(null);
        message.setPhoto(List.of(new PhotoSize()));
        message.setCaption("看看这个");

        MessageReceivedEvent event = MessageReceivedEvent.from(BotContext.from(wrap(message))).orElseThrow();

        assertNotNull(event);
        assertEquals("photo", event.messageType());
        assertEquals("看看这个", event.text());
    }

    @Test
    void serviceMessageWithoutRecordableTypeShouldBeEmpty() {
        Message message = baseMessage(-100123L, "supergroup");
        message.setText(null);

        assertTrue(MessageReceivedEvent.from(BotContext.from(wrap(message))).isEmpty());
    }

    @Test
    void textShouldBeTruncatedToMaxLength() {
        String longText = "话".repeat(MessageReceivedEvent.MAX_TEXT_LENGTH + 500);
        BotContext context = messageContext(-100123L, "supergroup", longText);

        MessageReceivedEvent event = MessageReceivedEvent.from(context).orElseThrow();

        assertNotNull(event);
        assertEquals(MessageReceivedEvent.MAX_TEXT_LENGTH, event.text().length());
    }

    @Test
    void nullContextOrNoMessageShouldBeEmpty() {
        assertTrue(MessageReceivedEvent.from(null).isEmpty());
        assertTrue(MessageReceivedEvent.from(BotContext.from(new Update())).isEmpty());
    }

    private BotContext messageContext(Long chatId, String chatType, String text) {
        Message message = baseMessage(chatId, chatType);
        message.setText(text);
        return BotContext.from(wrap(message));
    }

    private Message baseMessage(Long chatId, String chatType) {
        Message message = new Message();
        message.setMessageId(42);
        message.setDate(1756800000);
        message.setChat(Chat.builder().id(chatId).type(chatType).build());
        message.setFrom(User.builder()
                .id(999L)
                .isBot(false)
                .userName("tester")
                .firstName("Test")
                .lastName("User")
                .build());
        return message;
    }

    private Update wrap(Message message) {
        Update update = new Update();
        update.setMessage(message);
        return update;
    }
}
