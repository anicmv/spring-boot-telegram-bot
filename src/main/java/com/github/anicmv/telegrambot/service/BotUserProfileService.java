package com.github.anicmv.telegrambot.service;

import com.github.anicmv.telegrambot.model.BotUserProfile;
import com.github.anicmv.telegrambot.repository.BotUserRepository;
import java.util.Collection;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 用户资料应用服务。
 */
@Service
public class BotUserProfileService {

    private final BotUserRepository repository;

    public BotUserProfileService(BotUserRepository repository) {
        this.repository = repository;
    }

    public void upsert(BotUserProfile profile) {
        repository.upsert(profile);
    }

    public Optional<BotUserProfile> findByTelegramId(Long telegramId) {
        return repository.findByTelegramId(telegramId);
    }

    /**
     * 用户资料是否由全局自动注册在近期创建，用于识别“首次使用”体验。
     */
    public boolean registeredRecently(Long telegramId) {
        return repository.createdWithinMinutes(telegramId, 2);
    }

    public Optional<BotUserProfile> findRandomWithAvatar() {
        return repository.findRandomWithAvatar();
    }

    public Optional<BotUserProfile> findRandomWithAvatarExcluding(Collection<Long> telegramIds) {
        return repository.findRandomWithAvatarExcluding(telegramIds);
    }
}
