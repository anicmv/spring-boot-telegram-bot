package com.github.anicmv.telegrambot.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.anicmv.telegrambot.entity.ChatMessageEntity;
import com.github.anicmv.telegrambot.mapper.ChatMessageMapper;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 群聊消息记录仓储。
 */
@Log4j2
@Repository
public class ChatMessageRepository {

    private final ChatMessageMapper chatMessageMapper;

    public ChatMessageRepository(ChatMessageMapper chatMessageMapper) {
        this.chatMessageMapper = chatMessageMapper;
    }

    /**
     * 插入一条消息记录；唯一键（chat_id, telegram_message_id）冲突时静默跳过。
     */
    public void insert(ChatMessageEntity entity) {
        if (entity == null) {
            return;
        }
        try {
            chatMessageMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            log.debug("消息已记录过，跳过: chatId={}, messageId={}", entity.getChatId(), entity.getTelegramMessageId());
        }
    }

    /**
     * 查询白名单群内仍有未分析消息（chat_message.id 大于该用户画像游标）的用户 ID 列表。
     */
    public List<Long> findDistinctUserIdsWithNewMessages(Collection<Long> chatIds) {
        if (chatIds == null || chatIds.isEmpty()) {
            return List.of();
        }
        QueryWrapper<ChatMessageEntity> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT telegram_user_id")
                .in("chat_id", chatIds)
                .apply("id > COALESCE((SELECT last_analyzed_message_id FROM user_profile"
                        + " WHERE user_profile.telegram_user_id = chat_message.telegram_user_id), 0)");
        return chatMessageMapper.selectList(wrapper).stream()
                .map(ChatMessageEntity::getTelegramUserId)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 按用户查询游标之后的增量消息，按 id 升序，最多 limit 条。
     */
    public List<ChatMessageEntity> findNewerThanByUser(Collection<Long> chatIds, Long userId, Long cursorId, int limit) {
        if (chatIds == null || chatIds.isEmpty() || userId == null) {
            return List.of();
        }
        LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<ChatMessageEntity>()
                .in(ChatMessageEntity::getChatId, chatIds)
                .eq(ChatMessageEntity::getTelegramUserId, userId)
                .gt(ChatMessageEntity::getId, cursorId == null ? 0L : cursorId)
                .orderByAsc(ChatMessageEntity::getId)
                .last("LIMIT " + Math.max(1, limit));
        return chatMessageMapper.selectList(wrapper);
    }
}
