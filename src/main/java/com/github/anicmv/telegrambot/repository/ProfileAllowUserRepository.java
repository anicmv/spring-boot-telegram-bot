package com.github.anicmv.telegrambot.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.anicmv.telegrambot.entity.ProfileAllowUserEntity;
import com.github.anicmv.telegrambot.mapper.ProfileAllowUserMapper;
import com.github.anicmv.telegrambot.model.ProfileAllowStatus;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * @author anicmv
 * @date 2026/9/5
 * @description /profile 命令白名单申请仓储：申请落 PENDING，管理员授权转 APPROVED/DENIED。
 */
@Repository
public class ProfileAllowUserRepository {

    private final ProfileAllowUserMapper profileAllowUserMapper;

    public ProfileAllowUserRepository(ProfileAllowUserMapper profileAllowUserMapper) {
        this.profileAllowUserMapper = profileAllowUserMapper;
    }

    public Optional<ProfileAllowUserEntity> findByTelegramId(Long telegramUserId) {
        if (telegramUserId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(selectByTelegramId(telegramUserId));
    }

    public boolean isApproved(Long telegramUserId) {
        ProfileAllowUserEntity entity = telegramUserId == null ? null : selectByTelegramId(telegramUserId);
        return entity != null && ProfileAllowStatus.APPROVED.name().equals(entity.getStatus());
    }

    /** 发起（或重新发起）申请：落 PENDING 并清空历史授权人。 */
    public void createOrResetRequest(Long telegramUserId) {
        saveStatus(telegramUserId, ProfileAllowStatus.PENDING, null);
    }

    /** 管理员授权。 */
    public void approve(Long telegramUserId, Long adminId) {
        saveStatus(telegramUserId, ProfileAllowStatus.APPROVED, adminId);
    }

    /** 管理员拒绝。 */
    public void deny(Long telegramUserId) {
        saveStatus(telegramUserId, ProfileAllowStatus.DENIED, null);
    }

    private void saveStatus(Long telegramUserId, ProfileAllowStatus status, Long grantedBy) {
        if (telegramUserId == null) {
            return;
        }
        ProfileAllowUserEntity existing = selectByTelegramId(telegramUserId);
        if (existing == null) {
            ProfileAllowUserEntity entity = new ProfileAllowUserEntity();
            entity.setTelegramUserId(telegramUserId);
            entity.setStatus(status.name());
            entity.setGrantedBy(grantedBy);
            try {
                profileAllowUserMapper.insert(entity);
                return;
            } catch (DuplicateKeyException e) {
                // select-then-insert 窗口内并发插入撞 uk_profile_allow_user_telegram_id：重查后转 update
                existing = selectByTelegramId(telegramUserId);
                if (existing == null) {
                    throw e;
                }
            }
        }
        profileAllowUserMapper.update(null, new LambdaUpdateWrapper<ProfileAllowUserEntity>()
                .eq(ProfileAllowUserEntity::getId, existing.getId())
                .set(ProfileAllowUserEntity::getStatus, status.name())
                .set(ProfileAllowUserEntity::getGrantedBy, grantedBy));
    }

    private ProfileAllowUserEntity selectByTelegramId(Long telegramUserId) {
        return profileAllowUserMapper.selectOne(new LambdaQueryWrapper<ProfileAllowUserEntity>()
                .eq(ProfileAllowUserEntity::getTelegramUserId, telegramUserId)
                .last("LIMIT 1"));
    }
}
