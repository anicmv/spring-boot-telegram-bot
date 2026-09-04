package com.github.anicmv.telegrambot.repository;

import com.github.anicmv.telegrambot.entity.ChatImageEntity;
import com.github.anicmv.telegrambot.mapper.ChatImageMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 群聊静态图片仓储。
 */
@Log4j2
@Repository
public class ChatImageRepository {

    private final ChatImageMapper chatImageMapper;

    public ChatImageRepository(ChatImageMapper chatImageMapper) {
        this.chatImageMapper = chatImageMapper;
    }

    /**
     * 插入一条图片记录；file_unique_id 冲突（同一图片重复发送）时静默跳过。
     */
    public void insert(ChatImageEntity entity) {
        if (entity == null) {
            return;
        }
        try {
            chatImageMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            log.debug("图片已入库，跳过: fileUniqueId={}", entity.getFileUniqueId());
        }
    }
}
