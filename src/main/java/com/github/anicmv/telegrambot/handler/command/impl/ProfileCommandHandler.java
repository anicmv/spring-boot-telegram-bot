package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.entity.UserProfileEntity;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.Replier;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.model.BotUserProfile;
import com.github.anicmv.telegrambot.repository.BotUserRepository;
import com.github.anicmv.telegrambot.repository.ChatMessageRepository;
import com.github.anicmv.telegrambot.repository.UserProfileRepository;
import com.github.anicmv.telegrambot.service.ProfileAnalysisService;
import com.github.anicmv.telegrambot.utils.BotUtil;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description /profile 命令处理器：查看用户画像。
 * /profile 查自己；回复消息发 /profile 查对方；/profile @username 查他人（需配置放开）。
 */
@Log4j2
@BotCommand(value = BotConstant.CMD_PROFILE, description = "查看用户画像：/profile 查自己，回复消息发 /profile 查对方")
@Component
public class ProfileCommandHandler implements BotCommandHandler {

    private static final String LOADING_TEXT = "🤖 正在查询...";
    private static final DateTimeFormatter RANGE_FORMAT = DateTimeFormatter.ofPattern("MM-dd");

    private final Messenger messenger;
    private final UserProfileRepository userProfileRepository;
    private final BotUserRepository botUserRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProfileAnalysisService profileAnalysisService;
    private final BotProperties botProperties;
    private final TaskExecutor botBackgroundExecutor;

    public ProfileCommandHandler(Messenger messenger,
                                 UserProfileRepository userProfileRepository,
                                 BotUserRepository botUserRepository,
                                 ChatMessageRepository chatMessageRepository,
                                 ProfileAnalysisService profileAnalysisService,
                                 BotProperties botProperties,
                                 @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor) {
        this.messenger = messenger;
        this.userProfileRepository = userProfileRepository;
        this.botUserRepository = botUserRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.profileAnalysisService = profileAnalysisService;
        this.botProperties = botProperties;
        this.botBackgroundExecutor = botBackgroundExecutor;
    }

    @Override
    public void execute(BotContext context) {
        Set<Long> allowUserIds = botProperties.getProfile().getAllowUserIds();
        if (!allowUserIds.isEmpty() && !allowUserIds.contains(context.userId())) {
            replyPlain(context, "你不在画像命令白名单内，无法使用 /profile。");
            return;
        }
        ProfileQuery query = resolveQuery(context);
        if (query.rejected()) {
            replyPlain(context, query.rejectMessage());
            return;
        }
        Integer progressMessageId = sendLoadingMessage(context);
        try {
            botBackgroundExecutor.execute(() -> answerInBackground(context, query.targetUserId(), progressMessageId));
        } catch (RejectedExecutionException e) {
            log.warn("画像查询任务被拒绝. chatId={}", context.chatId(), e);
            deliver(context, progressMessageId, BotUtil.escapeHtml("系统繁忙，请稍后重试。"));
        }
    }

    private ProfileQuery resolveQuery(BotContext context) {
        String argument = extractFirstArgument(context.text());
        if (argument.startsWith("@")) {
            if (!botProperties.getProfile().isAllowQueryOthers()) {
                return ProfileQuery.rejected("暂未开启直接查询他人画像。查自己请用 /profile，查对方可回复其消息后发 /profile。");
            }
            String username = argument.substring(1).trim();
            return botUserRepository.findByUsername(username)
                    .map(user -> ProfileQuery.of(user.telegramId()))
                    .orElseGet(() -> ProfileQuery.rejected("未找到用户 @" + username + "。"));
        }
        Message message = context.message();
        if (message != null && message.getReplyToMessage() != null
                && message.getReplyToMessage().getFrom() != null) {
            User repliedFrom = message.getReplyToMessage().getFrom();
            if (Boolean.TRUE.equals(repliedFrom.getIsBot())) {
                return ProfileQuery.rejected("机器人账号不支持生成画像。");
            }
            return ProfileQuery.of(repliedFrom.getId());
        }
        return ProfileQuery.of(context.userId());
    }

    static String extractFirstArgument(String text) {
        if (text == null) {
            return "";
        }
        String[] parts = text.trim().split("\\s+");
        return parts.length < 2 ? "" : parts[1];
    }

    private void answerInBackground(BotContext context, Long targetUserId, Integer progressMessageId) {
        String text;
        try {
            UserProfileEntity profile = userProfileRepository.findByTelegramId(targetUserId).orElse(null);
            if (profile == null) {
                profile = generateOnDemand(context, targetUserId, progressMessageId);
            }
            text = profile == null
                    ? BotUtil.escapeHtml("该用户还没有画像数据（需要白名单群内有可分析的聊天记录）。")
                    : formatProfile(targetUserId, profile);
        } catch (Exception e) {
            log.error("画像查询失败. targetUserId={}", targetUserId, e);
            text = BotUtil.escapeHtml("画像查询失败，请稍后重试。");
        }
        deliver(context, progressMessageId, text);
    }

    /**
     * 库里没有画像时现场跑一轮分析（与定时任务同一批处理逻辑，单批最多 batch-message-limit 条），
     * 生成后立即返回画像；后续增量仍由定时任务滚动合并。
     */
    private UserProfileEntity generateOnDemand(BotContext context, Long targetUserId, Integer progressMessageId) {
        editProgress(context, progressMessageId, "🔬 首次生成画像，正在分析聊天记录，可能需要几十秒...");
        try {
            ProfileAnalysisService.Result result = profileAnalysisService.analyzeUser(targetUserId);
            if (result == ProfileAnalysisService.Result.SKIPPED) {
                return null;
            }
        } catch (Exception e) {
            log.error("画像现场生成失败. targetUserId={}", targetUserId, e);
        }
        return userProfileRepository.findByTelegramId(targetUserId).orElse(null);
    }

    private void editProgress(BotContext context, Integer progressMessageId, String text) {
        if (progressMessageId != null) {
            try {
                messenger.editMessageText(context.chatId(), progressMessageId, BotUtil.escapeHtml(text), "HTML");
            } catch (Exception e) {
                log.warn("画像生成进度更新失败. chatId={}", context.chatId(), e);
            }
        }
    }

    /**
     * 渲染画像报告（HTML 解析模式）：标题 + 样本统计 + 一句话人设引用 + 可折叠长文正文 + 页脚。
     */
    String formatProfile(Long targetUserId, UserProfileEntity profile) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户画像：").append(displayName(targetUserId));
        builder.append('\n').append(statsLine(profile));
        if (isNotBlank(profile.getSummary())) {
            builder.append("\n\n<blockquote>").append(BotUtil.escapeHtml(profile.getSummary().trim())).append("</blockquote>");
        }
        if (isNotBlank(profile.getReport())) {
            builder.append("\n\n<blockquote expandable>")
                    .append(BotUtil.escapeHtml(profile.getReport().trim()))
                    .append("</blockquote>");
        }
        builder.append("\n\n").append(footerLine(profile));
        return builder.toString();
    }

    private String displayName(Long targetUserId) {
        String mention = "<a href=\"tg://user?id=" + targetUserId + "\">";
        return botUserRepository.findByTelegramId(targetUserId)
                .map(BotUserProfile::username)
                .filter(ProfileCommandHandler::isNotBlank)
                .map(username -> mention + "@" + BotUtil.escapeHtml(username) + "</a>")
                .orElseGet(() -> "ID " + targetUserId);
    }

    private String statsLine(UserProfileEntity profile) {
        StringBuilder line = new StringBuilder("样本 ");
        line.append(profile.getAnalyzedMessageCount() == null ? 0 : profile.getAnalyzedMessageCount()).append(" 条");
        ChatMessageRepository.UserMessageStats stats = chatMessageRepository.findStatsByUser(
                botProperties.getProfile().getRecordGroupIds(),
                profile.getTelegramUserId(),
                profile.getLastAnalyzedMessageId());
        if (stats.chatCount() > 0) {
            line.append(" ｜ 群聊 ").append(stats.chatCount()).append(" 个");
        }
        if (stats.firstSentAt() != null && stats.lastSentAt() != null) {
            line.append(" ｜ 时间范围 ")
                    .append(stats.firstSentAt().format(RANGE_FORMAT))
                    .append(" ~ ")
                    .append(stats.lastSentAt().format(RANGE_FORMAT));
        }
        return line.toString();
    }

    private String footerLine(UserProfileEntity profile) {
        StringBuilder footer = new StringBuilder("Powered by ");
        footer.append(isNotBlank(profile.getModel()) ? BotUtil.escapeHtml(profile.getModel()) : "AI");
        if (profile.getTotalTokens() != null && profile.getTotalTokens() > 0) {
            footer.append(" ｜ ").append(String.format("%,d", profile.getTotalTokens())).append(" tokens");
        }
        // Telegram 服务端不支持 span textcolor 自定义色，用 code 渲染成灰底弱化样式
        return "<code>" + footer + "</code>";
    }

    private Integer sendLoadingMessage(BotContext context) {
        return Replier.of(context, messenger).textAndReturnId(LOADING_TEXT);
    }

    private void deliver(BotContext context, Integer progressMessageId, String text) {
        if (progressMessageId == null) {
            messenger.sendHtmlText(context.chatId(), text);
            return;
        }
        messenger.editMessageText(context.chatId(), progressMessageId, text, "HTML");
    }

    private void replyPlain(BotContext context, String text) {
        Replier.of(context, messenger).text(text);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record ProfileQuery(Long targetUserId, String rejectMessage) {
        static ProfileQuery of(Long targetUserId) {
            return new ProfileQuery(targetUserId, null);
        }

        static ProfileQuery rejected(String message) {
            return new ProfileQuery(null, message);
        }

        boolean rejected() {
            return targetUserId == null;
        }
    }
}
