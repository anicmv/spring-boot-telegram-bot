package com.github.anicmv.telegrambot.listener;

import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.event.MessageReceivedEvent;
import com.github.anicmv.telegrambot.messenger.Messenger;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author anicmv
 * @date 2026/9/5
 * @description 关键词通知监听器：监听群消息中的关键词，匹配时将消息链接发送给指定用户。
 * 支持多关键词、群白名单、异步发送，不阻塞主链路。
 */
@Log4j2
@Component
public class KeywordNotifyListener {

    private final BotProperties properties;
    private final Messenger messenger;
    private final TaskExecutor botBackgroundExecutor;

    public KeywordNotifyListener(BotProperties properties,
                                 Messenger messenger,
                                 @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor) {
        this.properties = properties;
        this.messenger = messenger;
        this.botBackgroundExecutor = botBackgroundExecutor;
    }

    @EventListener
    public void onMessage(MessageReceivedEvent event) {
        BotProperties.KeywordNotify config = properties.getKeywordNotify();

        if (!config.isEnabled() || config.getNotifyUserId() == null) {
            return;
        }

        if (!event.isGroupChat()) {
            return;
        }

        Set<Long> groupIds = config.getGroupIds();
        if (!groupIds.isEmpty() && !groupIds.contains(event.chatId())) {
            return;
        }

        String text = event.text();
        if (text == null || text.isBlank()) {
            return;
        }

        Set<String> keywords = config.getKeywords();
        if (keywords.isEmpty()) {
            return;
        }

        boolean matched = keywords.stream().anyMatch(text::contains);
        if (!matched) {
            return;
        }

        try {
            botBackgroundExecutor.execute(() -> notifySafely(event, config.getNotifyUserId()));
        } catch (RejectedExecutionException e) {
            log.warn("关键词通知任务被拒绝: chatId={}, messageId={}", event.chatId(), event.telegramMessageId());
        }
    }

    private void notifySafely(MessageReceivedEvent event, Long notifyUserId) {
        try {
            String messageLink = buildMessageLink(event);
            String notifyText = String.format(
                    "🔔 检测到关键词消息\n\n" +
                    "发送者: %s\n" +
                    "内容: %s\n\n" +
                    "查看消息: %s",
                    formatSender(event),
                    truncate(event.text(), 100),
                    messageLink
            );
            messenger.sendText(notifyUserId, notifyText);
            log.info("关键词通知已发送: chatId={}, messageId={}, notifyUserId={}",
                    event.chatId(), event.telegramMessageId(), notifyUserId);
        } catch (Exception e) {
            log.error("关键词通知发送失败: chatId={}, messageId={}, notifyUserId={}",
                    event.chatId(), event.telegramMessageId(), notifyUserId, e);
        }
    }

    private String buildMessageLink(MessageReceivedEvent event) {
        Long chatId = event.chatId();
        Long messageId = event.telegramMessageId();

        if (chatId == null || messageId == null) {
            return "(无法生成链接)";
        }

        // 私有超级群链接格式: https://t.me/c/<chatId_without_-100_prefix>/<messageId>
        // 公开群/频道链接格式: https://t.me/<username>/<messageId>
        // 这里统一使用私有格式，因为 event 中没有 chat username 信息
        String chatIdStr = chatId.toString();
        if (chatIdStr.startsWith("-100")) {
            chatIdStr = chatIdStr.substring(4);
            return String.format("https://t.me/c/%s/%d", chatIdStr, messageId);
        }

        return String.format("https://t.me/c/%s/%d", chatIdStr, messageId);
    }

    private String formatSender(MessageReceivedEvent event) {
        if (event.nickname() != null) {
            return event.nickname();
        }
        if (event.username() != null) {
            return "@" + event.username();
        }
        return "用户" + event.userId();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
