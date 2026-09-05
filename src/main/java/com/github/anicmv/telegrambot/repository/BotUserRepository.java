package com.github.anicmv.telegrambot.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.anicmv.telegrambot.model.BotUserProfile;
import com.github.anicmv.telegrambot.entity.BotUserEntity;
import com.github.anicmv.telegrambot.mapper.BotUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description Bot 用户资料仓储。
 */
@Repository
public class BotUserRepository {

    private final BotUserMapper botUserMapper;

    public BotUserRepository(BotUserMapper botUserMapper) {
        this.botUserMapper = botUserMapper;
    }

    public void upsert(BotUserProfile profile) {
        if (profile == null || profile.telegramId() == null) {
            return;
        }
        BotUserEntity entity = new BotUserEntity();
        entity.setUsername(profile.username());
        entity.setNickname(profile.nickname());
        entity.setTelegramId(profile.telegramId());
        entity.setAvatarFileId(profile.avatarFileId());
        entity.setAvatarData(profile.avatarData());

        BotUserEntity existing = selectByTelegramId(profile.telegramId());
        if (existing == null) {
            try {
                botUserMapper.insert(entity);
                return;
            } catch (DuplicateKeyException e) {
                // select-then-insert 窗口内并发插入撞 uk_bot_user_telegram_id：重查后转 update
                existing = selectByTelegramId(profile.telegramId());
                if (existing == null) {
                    throw e;
                }
            }
        }
        entity.setUserId(existing.getUserId());
        botUserMapper.updateById(entity);
    }

    private BotUserEntity selectByTelegramId(Long telegramId) {
        return botUserMapper.selectOne(new LambdaQueryWrapper<BotUserEntity>()
                .eq(BotUserEntity::getTelegramId, telegramId)
                .last("LIMIT 1"));
    }

    /**
     * 判断用户记录是否在最近 N 分钟内创建（使用数据库时钟，避免应用与 DB 时区差异）。
     */
    public boolean createdWithinMinutes(Long telegramId, int minutes) {
        if (telegramId == null || minutes <= 0) {
            return false;
        }
        Long count = botUserMapper.selectCount(new LambdaQueryWrapper<BotUserEntity>()
                .eq(BotUserEntity::getTelegramId, telegramId)
                .apply("created_at > DATE_SUB(NOW(), INTERVAL {0} MINUTE)", minutes));
        return count != null && count > 0;
    }

    public Optional<BotUserProfile> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        BotUserEntity entity = botUserMapper.selectOne(new LambdaQueryWrapper<BotUserEntity>()
                .eq(BotUserEntity::getUsername, username.trim())
                .last("LIMIT 1"));
        return Optional.ofNullable(toProfile(entity));
    }

    public Optional<BotUserProfile> findByTelegramId(Long telegramId) {
        if (telegramId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(toProfile(selectByTelegramId(telegramId)));
    }

    public Optional<BotUserProfile> findRandomWithAvatar() {
        BotUserEntity entity = botUserMapper.selectOne(withAvatarWrapper().last("ORDER BY RAND() LIMIT 1"));
        return Optional.ofNullable(toProfile(entity));
    }

    /**
     * 随机取一个带头像的卡池用户，排除给定 Telegram ID 集合（含 null 元素自动忽略）。
     */
    public Optional<BotUserProfile> findRandomWithAvatarExcluding(Collection<Long> telegramIds) {
        List<Long> ids = telegramIds == null ? List.of() : telegramIds.stream().filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return findRandomWithAvatar();
        }
        LambdaQueryWrapper<BotUserEntity> wrapper = withAvatarWrapper()
                .notIn(BotUserEntity::getTelegramId, ids)
                .last("ORDER BY RAND() LIMIT 1");
        BotUserEntity entity = botUserMapper.selectOne(wrapper);
        return Optional.ofNullable(toProfile(entity));
    }

    private LambdaQueryWrapper<BotUserEntity> withAvatarWrapper() {
        return new LambdaQueryWrapper<BotUserEntity>()
                .eq(BotUserEntity::getMatchmakerEnabled, true)
                .isNotNull(BotUserEntity::getAvatarFileId)
                .ne(BotUserEntity::getAvatarFileId, "");
    }

    private BotUserProfile toProfile(BotUserEntity entity) {
        if (entity == null) {
            return null;
        }
        return new BotUserProfile(
                entity.getUserId(),
                entity.getUsername(),
                entity.getNickname(),
                entity.getTelegramId(),
                entity.getAvatarFileId(),
                entity.getAvatarData()
        );
    }
}
