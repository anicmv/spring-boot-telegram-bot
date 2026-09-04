package com.github.anicmv.telegrambot.handler.inline.chosen.impl;

import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.handler.inline.chosen.ChosenInlineQueryResultHandler;
import com.github.anicmv.telegrambot.service.BotUserProfileService;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.utils.BotUtil;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.BotUserProfile;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * @author anicmv
 * @date 2026/3/21
 * @description 红娘系统已选内联结果处理器。
 */
@Log4j2
@BotInline(BotConstant.INLINE_ID_MATCHMAKER)
@Component
public class MatchmakerChosenInlineQueryHandler implements ChosenInlineQueryResultHandler {

    private static final String PLACEHOLDER_IMAGE = "https://jpg.moe/i/rp8dpcn2.jpeg";
    private static final int MAX_DRAW_ATTEMPTS = 3;
    private static final String EMPTY_POOL_CAPTION = "红娘系统今天摸鱼了，暂时没摇到缘分（先拉朋友来 /start 扩充卡池）。";
    private static final String RETRY_CAPTION = "本轮没摇到合适的缘分，稍后再试。";
    private static final String LEADING_CAPTION_PREFIX = "\u200E🎉 ";

    private final Messenger messenger;
    private final BotUserProfileService botUserProfileService;
    private final BotProperties properties;

    public MatchmakerChosenInlineQueryHandler(Messenger messenger,
                                              BotUserProfileService botUserProfileService,
                                              BotProperties properties) {
        this.messenger = messenger;
        this.botUserProfileService = botUserProfileService;
        this.properties = properties;
    }

    @Override
    public void execute(BotContext context) {
        Long currentUserId = context.chosenInlineQuery().getFrom().getId();
        String inlineMessageId = context.chosenInlineQuery().getInlineMessageId();
        Set<Long> tried = new HashSet<>();
        tried.add(currentUserId);
        for (int attempt = 0; attempt < MAX_DRAW_ATTEMPTS; attempt++) {
            Optional<BotUserProfile> random = botUserProfileService.findRandomWithAvatarExcluding(tried);
            if (random.isEmpty()) {
                messenger.editInlineMessagePhoto(inlineMessageId, PLACEHOLDER_IMAGE,
                        tried.size() > 1 ? RETRY_CAPTION : EMPTY_POOL_CAPTION, null);
                return;
            }
            BotUserProfile profile = random.get();
            tried.add(profile.telegramId());
            String avatarFileId = resolveAvatarFileId(profile);
            boolean edited = messenger.editInlineMessagePhoto(inlineMessageId, avatarFileId,
                    buildCaption(context.chosenInlineQuery().getFrom(), profile), "MarkdownV2");
            if (edited) {
                return;
            }
            log.warn("红娘头像失效，换人重摇: telegramId={}", profile.telegramId());
            refreshFailedAvatar(profile);
        }
        messenger.editInlineMessagePhoto(inlineMessageId, PLACEHOLDER_IMAGE, RETRY_CAPTION, null);
    }

    /**
     * 编辑失败后尽力刷新该用户库里的头像 file_id，下次摇号即可用。
     */
    private void refreshFailedAvatar(BotUserProfile profile) {
        String fresh = messenger.getUserAvatarFileId(profile.telegramId());
        if (fresh != null && !fresh.isBlank() && !fresh.equals(profile.avatarFileId())) {
            botUserProfileService.upsert(new BotUserProfile(profile.userId(), profile.username(),
                    profile.nickname(), profile.telegramId(), fresh, null));
        }
    }

    private String buildCaption(User from, BotUserProfile profile) {
        String requester = BotUtil.mentionMarkdownV2(from);
        String matched = BotUtil.mentionMarkdownV2(profile.telegramId(), buildDisplayName(profile));
        String relationWord = "⭐️\uD83D\uDE21";
        // Anchor the leading emoji before the mention to avoid Telegram client reordering it.
        String caption = BotUtil.escapeMarkdownV2(LEADING_CAPTION_PREFIX)
                + requester
                + BotUtil.escapeMarkdownV2("摇到的" + relationWord + "是 ")
                + matched;
        return caption;
    }

    /**
     * 摇号时刷新头像 file_id：注册时存的 file_id 内 file_reference 会过期，
     * 直接用于 EditMessageMedia 会 400 FILE_REFERENCE_EXPIRED。
     * 优先取用户当前头像的新 file_id；取不到（用户删了头像）则用落库的头像字节重传换新 id；
     * 都失败才退回存储值。刷新成功时同步更新库，避免下次再刷。
     */
    private String resolveAvatarFileId(BotUserProfile profile) {
        String fresh = messenger.getUserAvatarFileId(profile.telegramId());
        if (fresh == null && profile.avatarData() != null && properties.getChannelId() != null) {
            fresh = messenger.uploadPhotoBytes(properties.getChannelId(), profile.avatarData());
        }
        if (fresh == null || fresh.isBlank()) {
            return profile.avatarFileId();
        }
        if (!fresh.equals(profile.avatarFileId())) {
            botUserProfileService.upsert(new BotUserProfile(profile.userId(), profile.username(),
                    profile.nickname(), profile.telegramId(), fresh, null));
        }
        return fresh;
    }

    private String buildDisplayName(BotUserProfile profile) {
        if (profile.nickname() != null && !profile.nickname().isBlank()) {
            return profile.nickname();
        }
        if (profile.username() != null && !profile.username().isBlank()) {
            return profile.username();
        }
        if (profile.telegramId() != null) {
            return "@" + profile.telegramId();
        }
        return "神秘缘分";
    }
}
