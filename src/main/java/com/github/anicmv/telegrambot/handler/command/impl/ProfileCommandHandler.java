package com.github.anicmv.telegrambot.handler.command.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.entity.UserProfileEntity;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.repository.BotUserRepository;
import com.github.anicmv.telegrambot.repository.UserProfileRepository;
import com.github.anicmv.telegrambot.utils.BotUtil;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
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

    private final Messenger messenger;
    private final UserProfileRepository userProfileRepository;
    private final BotUserRepository botUserRepository;
    private final BotProperties botProperties;
    private final ObjectMapper objectMapper;
    private final TaskExecutor botBackgroundExecutor;

    public ProfileCommandHandler(Messenger messenger,
                                 UserProfileRepository userProfileRepository,
                                 BotUserRepository botUserRepository,
                                 BotProperties botProperties,
                                 ObjectMapper objectMapper,
                                 @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor) {
        this.messenger = messenger;
        this.userProfileRepository = userProfileRepository;
        this.botUserRepository = botUserRepository;
        this.botProperties = botProperties;
        this.objectMapper = objectMapper;
        this.botBackgroundExecutor = botBackgroundExecutor;
    }

    @Override
    public void execute(BotContext context) {
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
            deliver(context, progressMessageId, BotUtil.escapeMarkdownV2("系统繁忙，请稍后重试。"));
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
            return ProfileQuery.of(message.getReplyToMessage().getFrom().getId());
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
            text = profile == null
                    ? BotUtil.escapeMarkdownV2("该用户还没有画像数据，群聊消息积累后将自动生成。")
                    : formatProfile(profile);
        } catch (Exception e) {
            log.error("画像查询失败. targetUserId={}", targetUserId, e);
            text = BotUtil.escapeMarkdownV2("画像查询失败，请稍后重试。");
        }
        deliver(context, progressMessageId, text);
    }

    private String formatProfile(UserProfileEntity profile) {
        StringBuilder builder = new StringBuilder();
        builder.append(BotUtil.escapeMarkdownV2("👤 用户画像"));
        if (isNotBlank(profile.getSummary())) {
            builder.append('\n').append(toQuoteBlock(profile.getSummary()));
        }
        appendListLine(builder, "🏷 兴趣", profile.getInterests());
        appendPersonalityLine(builder, profile.getPersonality());
        if (isNotBlank(profile.getActiveHours())) {
            builder.append('\n').append(BotUtil.escapeMarkdownV2("⏰ 活跃时段: " + profile.getActiveHours()));
        }
        appendListLine(builder, "💬 高频话题", profile.getFrequentTopics());
        if (profile.getAnalyzedMessageCount() != null && profile.getAnalyzedMessageCount() > 0) {
            builder.append('\n').append(BotUtil.escapeMarkdownV2("📊 已分析 " + profile.getAnalyzedMessageCount() + " 条消息"));
        }
        return builder.toString();
    }

    private void appendListLine(StringBuilder builder, String label, String jsonArray) {
        List<String> items = readStringList(jsonArray);
        if (items.isEmpty()) {
            return;
        }
        builder.append('\n').append(BotUtil.escapeMarkdownV2(label + ": " + String.join(", ", items)));
    }

    private void appendPersonalityLine(StringBuilder builder, String jsonArray) {
        Map<String, Object> traits = readMap(jsonArray);
        if (traits.isEmpty()) {
            return;
        }
        StringBuilder line = new StringBuilder();
        traits.forEach((key, value) -> {
            if (!line.isEmpty()) {
                line.append(" | ");
            }
            line.append(key).append(": ").append(value);
        });
        builder.append('\n').append(BotUtil.escapeMarkdownV2("🎭 性格: " + line));
    }

    private String toQuoteBlock(String value) {
        StringBuilder builder = new StringBuilder();
        for (String line : value.split("\n", -1)) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append('>').append(BotUtil.escapeMarkdownV2(line));
        }
        return builder.toString();
    }

    private List<String> readStringList(String jsonArray) {
        if (!isNotBlank(jsonArray)) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(jsonArray, new TypeReference<List<String>>() {
            });
            return values == null ? List.of() : values.stream().filter(ProfileCommandHandler::isNotBlank).toList();
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String jsonArray) {
        if (!isNotBlank(jsonArray)) {
            return Map.of();
        }
        try {
            Map<String, Object> values = objectMapper.readValue(jsonArray, new TypeReference<Map<String, Object>>() {
            });
            return values == null ? Map.of() : values;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private Integer sendLoadingMessage(BotContext context) {
        if (context.message() != null && context.message().getMessageId() != null) {
            return messenger.sendReplyTextAndReturnMessageId(context.chatId(), context.message().getMessageId(), LOADING_TEXT);
        }
        messenger.sendText(context.chatId(), LOADING_TEXT);
        return null;
    }

    private void deliver(BotContext context, Integer progressMessageId, String text) {
        if (progressMessageId == null) {
            messenger.sendMarkdownV2Text(context.chatId(), text);
            return;
        }
        messenger.editMessageText(context.chatId(), progressMessageId, text, "MarkdownV2");
    }

    private void replyPlain(BotContext context, String text) {
        if (context.message() != null && context.message().getMessageId() != null) {
            messenger.sendReplyText(context.chatId(), context.message().getMessageId(), text);
            return;
        }
        messenger.sendText(context.chatId(), text);
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
