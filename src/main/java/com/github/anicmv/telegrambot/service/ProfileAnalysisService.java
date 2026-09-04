package com.github.anicmv.telegrambot.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.entity.ChatMessageEntity;
import com.github.anicmv.telegrambot.entity.UserProfileEntity;
import com.github.anicmv.telegrambot.model.ProfileAnalysisStats;
import com.github.anicmv.telegrambot.repository.ChatMessageRepository;
import com.github.anicmv.telegrambot.repository.UserProfileRepository;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 用户画像分析服务：逐用户取增量消息，交由大模型生成/合并画像后落库。
 * 不依赖任何调度框架，可被任意触发方式（xxl-job、命令、测试）复用。
 */
@Log4j2
@Service
public class ProfileAnalysisService {

    private static final DateTimeFormatter MESSAGE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ChatMessageRepository chatMessageRepository;
    private final UserProfileRepository userProfileRepository;
    private final DeepSeekChatService deepSeekChatService;
    private final BotProperties botProperties;
    private final ObjectMapper objectMapper;

    public ProfileAnalysisService(ChatMessageRepository chatMessageRepository,
                                  UserProfileRepository userProfileRepository,
                                  DeepSeekChatService deepSeekChatService,
                                  BotProperties botProperties,
                                  ObjectMapper objectMapper) {
        this.chatMessageRepository = chatMessageRepository;
        this.userProfileRepository = userProfileRepository;
        this.deepSeekChatService = deepSeekChatService;
        this.botProperties = botProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 分析所有白名单群内有增量消息的用户。
     *
     * @param progress 进度回调（供调度日志展示）
     * @return 统计结果
     */
    public ProfileAnalysisStats analyzeAll(Consumer<String> progress) {
        BotProperties.Profile props = botProperties.getProfile();
        if (props.getRecordGroupIds() == null || props.getRecordGroupIds().isEmpty()) {
            progress.accept("记录群白名单为空，跳过分析");
            return new ProfileAnalysisStats(0, 0, 0, 0);
        }
        List<Long> userIds = chatMessageRepository.findDistinctUserIdsWithNewMessages(props.getRecordGroupIds());
        int success = 0;
        int failed = 0;
        int skipped = 0;
        for (Long userId : userIds) {
            Result result;
            try {
                result = analyzeUser(userId, props, progress);
            } catch (Exception e) {
                log.error("用户画像分析失败: userId={}", userId, e);
                progress.accept("用户 " + userId + " 分析失败: " + e.getMessage());
                result = Result.FAILED;
            }
            switch (result) {
                case SUCCESS -> success++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
            }
        }
        return new ProfileAnalysisStats(userIds.size(), success, failed, skipped);
    }

    private enum Result {
        SUCCESS, FAILED, SKIPPED
    }

    private Result analyzeUser(Long userId, BotProperties.Profile props, Consumer<String> progress) {
        UserProfileEntity oldProfile = userProfileRepository.findByTelegramId(userId).orElse(null);
        long cursor = oldProfile != null && oldProfile.getLastAnalyzedMessageId() != null
                ? oldProfile.getLastAnalyzedMessageId() : 0L;
        List<ChatMessageEntity> messages = chatMessageRepository.findNewerThanByUser(
                props.getRecordGroupIds(), userId, cursor, Math.max(1, props.getBatchMessageLimit()));
        if (messages.isEmpty()) {
            return Result.SKIPPED;
        }

        String userPrompt = buildUserPrompt(oldProfile, messages);
        ProfileModelOutput output = parse(deepSeekChatService.chat(props.getAnalysisPrompt(), userPrompt));
        if (output == null) {
            String retryPrompt = userPrompt + "\n\n注意：你上一次的输出格式不合法。请只输出一个 JSON 对象，不要输出任何解释或代码块标记。";
            output = parse(deepSeekChatService.chat(props.getAnalysisPrompt(), retryPrompt));
        }

        long newCursor = messages.get(messages.size() - 1).getId();
        if (output == null) {
            // 持续解析失败：推进游标避免毒消息死循环，但不覆盖旧画像
            advanceCursorOnly(userId, oldProfile, newCursor);
            log.warn("画像输出解析失败，推进游标跳过本轮: userId={}", userId);
            progress.accept("用户 " + userId + " 输出解析失败，已跳过本轮并推进游标");
            return Result.FAILED;
        }

        saveProfile(userId, oldProfile, output, newCursor, messages.size(), props.getModel());
        progress.accept("用户 " + userId + " 分析完成，共 " + messages.size() + " 条新消息");
        return Result.SUCCESS;
    }

    private void advanceCursorOnly(Long userId, UserProfileEntity oldProfile, long newCursor) {
        UserProfileEntity entity = oldProfile != null ? oldProfile : new UserProfileEntity();
        entity.setTelegramUserId(userId);
        entity.setLastAnalyzedMessageId(newCursor);
        userProfileRepository.upsert(entity);
    }

    private void saveProfile(Long userId, UserProfileEntity oldProfile, ProfileModelOutput output,
                             long newCursor, int messageCount, String model) {
        UserProfileEntity entity = oldProfile != null ? oldProfile : new UserProfileEntity();
        entity.setTelegramUserId(userId);
        entity.setSummary(output.summary());
        entity.setInterests(writeJson(output.interests()));
        entity.setPersonality(writeJson(output.personality()));
        entity.setActiveHours(output.activeHours());
        entity.setFrequentTopics(writeJson(output.frequentTopics()));
        int oldCount = oldProfile != null && oldProfile.getAnalyzedMessageCount() != null
                ? oldProfile.getAnalyzedMessageCount() : 0;
        entity.setAnalyzedMessageCount(oldCount + messageCount);
        entity.setLastAnalyzedMessageId(newCursor);
        entity.setModel(model);
        userProfileRepository.upsert(entity);
    }

    private String buildUserPrompt(UserProfileEntity oldProfile, List<ChatMessageEntity> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("【旧画像 JSON】\n");
        builder.append(oldProfile == null ? "无（首次分析）" : oldProfileJson(oldProfile)).append('\n');
        builder.append("\n【新聊天记录】\n");
        for (ChatMessageEntity message : messages) {
            builder.append('[')
                    .append(message.getSentAt() != null ? message.getSentAt().format(MESSAGE_TIME_FORMAT) : "未知时间")
                    .append("] ");
            boolean hasContent = message.getContent() != null && !message.getContent().isBlank();
            builder.append(hasContent ? message.getContent() : "[" + message.getMessageType() + "]");
            builder.append('\n');
        }
        builder.append("\n请输出更新后的用户画像 JSON。");
        return builder.toString();
    }

    private String oldProfileJson(UserProfileEntity profile) {
        try {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("summary", profile.getSummary());
            values.put("interests", readJson(profile.getInterests()));
            values.put("personality", readJson(profile.getPersonality()));
            values.put("active_hours", profile.getActiveHours());
            values.put("frequent_topics", readJson(profile.getFrequentTopics()));
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            log.warn("旧画像序列化失败: userId={}", profile.getTelegramUserId(), e);
            return "{}";
        }
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private ProfileModelOutput parse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return null;
        }
        String json = stripCodeFence(rawOutput.trim());
        try {
            return objectMapper.readValue(json, ProfileModelOutput.class);
        } catch (JsonProcessingException e) {
            log.warn("画像 JSON 解析失败: {}", abbreviate(json));
            return null;
        }
    }

    static String stripCodeFence(String text) {
        String result = text;
        if (result.startsWith("```")) {
            int firstNewline = result.indexOf('\n');
            if (firstNewline > 0) {
                result = result.substring(firstNewline + 1);
            }
            int lastFence = result.lastIndexOf("```");
            if (lastFence >= 0) {
                result = result.substring(0, lastFence);
            }
        }
        return result.trim();
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        String normalized = text.replace('\n', ' ');
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 197) + "...";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfileModelOutput(
            @JsonProperty("summary") String summary,
            @JsonProperty("interests") List<String> interests,
            @JsonProperty("personality") Map<String, Object> personality,
            @JsonProperty("active_hours") String activeHours,
            @JsonProperty("frequent_topics") List<String> frequentTopics
    ) {
    }
}
