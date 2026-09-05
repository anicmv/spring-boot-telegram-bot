package com.github.anicmv.telegrambot.listener;

import com.github.anicmv.telegrambot.entity.ChatMessageEntity;
import com.github.anicmv.telegrambot.event.MessageReceivedEvent;
import com.github.anicmv.telegrambot.listener.filter.GroupMessageFilter;
import com.github.anicmv.telegrambot.repository.ChatMessageRepository;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 群消息记录监听器：消息依次通过 {@link GroupMessageFilter} 过滤链（群聊、bot 发言、
 * 开关白名单等），全部通过才异步落库，不阻塞 update 主链路。新增过滤规则仅需实现过滤器接口。
 */
@Log4j2
@Component
public class GroupMessageRecordListener {

    private final ChatMessageRepository chatMessageRepository;
    private final TaskExecutor botBackgroundExecutor;
    private final List<GroupMessageFilter> filters;

    public GroupMessageRecordListener(ChatMessageRepository chatMessageRepository,
                                      @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor,
                                      @Qualifier("recordChain") List<GroupMessageFilter> filters) {
        this.chatMessageRepository = chatMessageRepository;
        this.botBackgroundExecutor = botBackgroundExecutor;
        this.filters = filters;
    }

    @EventListener
    public void onGroupMessage(MessageReceivedEvent event) {
        if (event == null || !passFilters(event)) {
            return;
        }
        try {
            botBackgroundExecutor.execute(() -> saveSafely(event));
        } catch (RejectedExecutionException e) {
            log.warn("群消息记录任务被拒绝: chatId={}, messageId={}", event.chatId(), event.telegramMessageId());
        }
    }

    private boolean passFilters(MessageReceivedEvent event) {
        for (GroupMessageFilter filter : filters) {
            if (!filter.accept(event)) {
                return false;
            }
        }
        return true;
    }

    private void saveSafely(MessageReceivedEvent event) {
        try {
            chatMessageRepository.insert(toEntity(event));
        } catch (Exception e) {
            log.error("群消息落库失败: chatId={}, messageId={}", event.chatId(), event.telegramMessageId(), e);
        }
    }

    private ChatMessageEntity toEntity(MessageReceivedEvent event) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setChatId(event.chatId());
        entity.setTelegramUserId(event.userId());
        entity.setUsername(event.username());
        entity.setNickname(event.nickname());
        entity.setMessageType(event.messageType());
        entity.setContent(event.text());
        entity.setTelegramMessageId(event.telegramMessageId());
        entity.setSentAt(event.sentAt());
        return entity;
    }
}
