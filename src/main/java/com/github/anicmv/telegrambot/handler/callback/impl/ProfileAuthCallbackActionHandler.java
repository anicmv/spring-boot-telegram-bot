package com.github.anicmv.telegrambot.handler.callback.impl;

import com.github.anicmv.telegrambot.annotation.BotCallback;
import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.handler.callback.CallbackActionHandler;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.BotUserProfile;
import com.github.anicmv.telegrambot.repository.BotUserRepository;
import com.github.anicmv.telegrambot.repository.ProfileAllowUserRepository;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * @author anicmv
 * @date 2026/9/5
 * @description /profile 白名单授权按钮回调：仅 admin 点击有效，
 * 授权/拒绝落 profile_allow_user 表，并把审批消息的按钮编辑为结果文案。
 */
@Log4j2
@BotCallback(BotConstant.CALLBACK_ACTION_PROFILE_AUTH)
@Component
public class ProfileAuthCallbackActionHandler implements CallbackActionHandler {

    private final Messenger messenger;
    private final ProfileAllowUserRepository profileAllowUserRepository;
    private final BotUserRepository botUserRepository;
    private final BotProperties botProperties;

    public ProfileAuthCallbackActionHandler(Messenger messenger,
                                            ProfileAllowUserRepository profileAllowUserRepository,
                                            BotUserRepository botUserRepository,
                                            BotProperties botProperties) {
        this.messenger = messenger;
        this.profileAllowUserRepository = profileAllowUserRepository;
        this.botUserRepository = botUserRepository;
        this.botProperties = botProperties;
    }

    @Override
    public void execute(BotContext context, String payload) {
        CallbackQuery query = context.callbackQuery();
        AuthDecision decision = parseDecision(payload);
        if (decision == null) {
            messenger.answerCallback(query.getId(), "按钮参数异常");
            return;
        }
        Long clickerId = query.getFrom() == null ? null : query.getFrom().getId();
        if (clickerId == null || !botProperties.getProfile().getAdminUserIds().contains(clickerId)) {
            messenger.answerCallback(query.getId(), "仅管理员可授权");
            return;
        }
        if (decision.approved()) {
            profileAllowUserRepository.approve(decision.targetUserId(), clickerId);
        } else {
            profileAllowUserRepository.deny(decision.targetUserId());
        }
        editResultMessage(context, query, decision, clickerId);
        messenger.answerCallback(query.getId(), decision.approved() ? "已授权" : "已拒绝");
    }

    private void editResultMessage(BotContext context, CallbackQuery query, AuthDecision decision, Long clickerId) {
        if (!(query.getMessage() instanceof Message message) || context.chatId() == null) {
            return;
        }
        String text = decision.approved()
                ? "✅ 管理员 " + displayName(clickerId) + " 已授权 " + displayName(decision.targetUserId())
                        + " 使用 /profile 用户画像，现在可以使用了。"
                : "❌ 管理员 " + displayName(clickerId) + " 已拒绝 " + displayName(decision.targetUserId())
                        + " 的 /profile 使用申请，可重新发送 /profile 再次申请。";
        messenger.editMessageText(context.chatId(), message.getMessageId(), text, null, List.of());
    }

    /** payload 形如 Y:123 / N:123；非法返回 null。 */
    private static AuthDecision parseDecision(String payload) {
        if (payload == null) {
            return null;
        }
        String[] parts = payload.split(":", 2);
        if (parts.length != 2 || !("Y".equals(parts[0]) || "N".equals(parts[0]))) {
            return null;
        }
        try {
            return new AuthDecision("Y".equals(parts[0]), Long.parseLong(parts[1].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String displayName(Long telegramUserId) {
        return botUserRepository.findByTelegramId(telegramUserId)
                .map(BotUserProfile::username)
                .filter(username -> username != null && !username.isBlank())
                .map(username -> "@" + username)
                .orElseGet(() -> "ID " + telegramUserId);
    }

    private record AuthDecision(boolean approved, Long targetUserId) {
    }
}
