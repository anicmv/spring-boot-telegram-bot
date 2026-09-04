package com.github.anicmv.telegrambot.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.anicmv.telegrambot.entity.UserProfileEntity;
import com.github.anicmv.telegrambot.mapper.UserProfileMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 用户画像仓储。
 */
@Repository
public class UserProfileRepository {

    private final UserProfileMapper userProfileMapper;

    public UserProfileRepository(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    public Optional<UserProfileEntity> findByTelegramId(Long telegramUserId) {
        if (telegramUserId == null) {
            return Optional.empty();
        }
        UserProfileEntity entity = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfileEntity>()
                .eq(UserProfileEntity::getTelegramUserId, telegramUserId)
                .last("LIMIT 1"));
        return Optional.ofNullable(entity);
    }

    /**
     * 按 telegram_user_id upsert：实体自带主键则直接更新，否则查重后插入或更新。
     */
    public void upsert(UserProfileEntity entity) {
        if (entity == null || entity.getTelegramUserId() == null) {
            return;
        }
        if (entity.getId() != null) {
            userProfileMapper.updateById(entity);
            return;
        }
        UserProfileEntity existing = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfileEntity>()
                .eq(UserProfileEntity::getTelegramUserId, entity.getTelegramUserId())
                .last("LIMIT 1"));
        if (existing == null) {
            userProfileMapper.insert(entity);
            return;
        }
        entity.setId(existing.getId());
        userProfileMapper.updateById(entity);
    }
}
