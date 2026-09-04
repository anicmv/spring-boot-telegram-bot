package com.github.anicmv.telegrambot.handler.common;

import com.github.anicmv.telegrambot.handler.HandlerResult;
import com.github.anicmv.telegrambot.handler.UpdateHandler;
import com.github.anicmv.telegrambot.service.BotUserProfileService;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.BotUserProfile;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.User;

/**
 * @author anicmv
 * @date 2026/3/21
 * @description 全局用户自动注册处理器，覆盖 command/inline/callback 等入口。
 */
@Order(-1000)
@Component
@Log4j2
public class AutoRegisterUserUpdateHandler implements UpdateHandler {

    private static final long AVATAR_RETRY_INTERVAL_MILLIS = Duration.ofHours(1).toMillis();

    private final BotUserProfileService botUserProfileService;
    private final Messenger messenger;
    private final TaskExecutor botBackgroundExecutor;
    private final Set<Long> avatarLoadingUsers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Long, Long> avatarLastLoadMillis = new ConcurrentHashMap<>();

    public AutoRegisterUserUpdateHandler(BotUserProfileService botUserProfileService,
                                         Messenger messenger,
                                         @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor) {
        this.botUserProfileService = botUserProfileService;
        this.messenger = messenger;
        this.botBackgroundExecutor = botBackgroundExecutor;
    }

    @Override
    public boolean supports(BotContext context) {
        return context != null && context.userId() != null;
    }

    @Override
    public HandlerResult handle(BotContext context) {
        User from = resolveFrom(context);
        if (from == null) {
            return HandlerResult.CONTINUE;
        }
        Optional<BotUserProfile> existing = botUserProfileService.findByTelegramId(from.getId());
        String avatarFileId = existing.map(BotUserProfile::avatarFileId).orElse(null);
        BotUserProfile profile = new BotUserProfile(
                existing.map(BotUserProfile::userId).orElse(null),
                from.getUserName(),
                buildNickname(from.getFirstName(), from.getLastName()),
                from.getId(),
                avatarFileId,
                existing.map(BotUserProfile::avatarData).orElse(null)
        );
        botUserProfileService.upsert(profile);
        if (avatarFileId == null || avatarFileId.isBlank()) {
            loadAvatarAsync(profile);
        }
        return HandlerResult.CONTINUE;
    }

    private void loadAvatarAsync(BotUserProfile profile) {
        Long telegramId = profile.telegramId();
        if (telegramId == null || isAvatarLoadCoolingDown(telegramId) || !avatarLoadingUsers.add(telegramId)) {
            return;
        }
        avatarLastLoadMillis.put(telegramId, System.currentTimeMillis());
        try {
            botBackgroundExecutor.execute(() -> loadAvatar(profile));
        } catch (RejectedExecutionException exception) {
            log.warn("Rejected avatar loading task: telegramId={}", telegramId, exception);
            avatarLoadingUsers.remove(telegramId);
        }
    }

    private boolean isAvatarLoadCoolingDown(Long telegramId) {
        Long lastLoadMillis = avatarLastLoadMillis.get(telegramId);
        return lastLoadMillis != null && System.currentTimeMillis() - lastLoadMillis < AVATAR_RETRY_INTERVAL_MILLIS;
    }

    private void loadAvatar(BotUserProfile profile) {
        Long telegramId = profile.telegramId();
        try {
            String avatarFileId = messenger.getUserAvatarFileId(telegramId);
            byte[] avatarData = avatarFileId == null || avatarFileId.isBlank()
                    ? null
                    : messenger.downloadFileBytes(avatarFileId);
            botUserProfileService.upsert(new BotUserProfile(
                    profile.userId(),
                    profile.username(),
                    profile.nickname(),
                    profile.telegramId(),
                    avatarFileId,
                    avatarData
            ));
        } finally {
            avatarLoadingUsers.remove(telegramId);
        }
    }

    private User resolveFrom(BotContext context) {
        if (context.message() != null) {
            return context.message().getFrom();
        }
        if (context.callbackQuery() != null) {
            return context.callbackQuery().getFrom();
        }
        if (context.inlineQuery() != null) {
            return context.inlineQuery().getFrom();
        }
        if (context.chosenInlineQuery() != null) {
            return context.chosenInlineQuery().getFrom();
        }
        return null;
    }

    private String buildNickname(String firstName, String lastName) {
        String fn = firstName == null ? "" : firstName.trim();
        String ln = lastName == null ? "" : lastName.trim();
        String full = (fn + " " + ln).trim();
        return full.isBlank() ? "telegram user id" : full;
    }

}
