package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.service.AiAccessControlService;
import com.github.anicmv.telegrambot.service.DeepSeekChatService;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.utils.BotUtil;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.Replier;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.config.BotProperties;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/5/1 17:12
 * @description /ai 命令处理器。
 */
@Log4j2
@BotCommand(value = BotConstant.CMD_AI, description = "调用 DeepSeek，对话格式：/ai 你的问题，或回复一条消息后发送 /ai")
@Component
public class AiCommandHandler implements BotCommandHandler {

    private static final int MAX_QUOTE_LENGTH = 800;
    private static final int EXPANDABLE_ANSWER_LENGTH = 600;
    private static final int EXPANDABLE_ANSWER_LINES = 12;

    private static final String THINKING_TEXT = "🤖 正在思考...";

    private final Messenger messenger;
    private final AiAccessControlService aiAccessControlService;
    private final DeepSeekChatService deepSeekChatService;
    private final BotProperties botProperties;
    private final TaskScheduler botScheduler;
    private final TaskExecutor botBackgroundExecutor;

    public AiCommandHandler(Messenger messenger,
                            AiAccessControlService aiAccessControlService,
                            DeepSeekChatService deepSeekChatService,
                            BotProperties botProperties,
                            @Qualifier("botScheduler") TaskScheduler botScheduler,
                            @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor) {
        this.messenger = messenger;
        this.aiAccessControlService = aiAccessControlService;
        this.deepSeekChatService = deepSeekChatService;
        this.botProperties = botProperties;
        this.botScheduler = botScheduler;
        this.botBackgroundExecutor = botBackgroundExecutor;
    }

    @Override
    public void execute(BotContext context) {
        String prompt = resolvePrompt(context);
        if (aiAccessControlService.isBlocked(context.userId())) {
            reply(context, formatMarkdownV2Response(prompt, aiAccessControlService.blockedMessage()));
            return;
        }
        Integer progressMessageId = sendThinkingMessage(context);
        try {
            botBackgroundExecutor.execute(() -> answerInBackground(context, prompt, progressMessageId));
        } catch (RejectedExecutionException e) {
            log.warn("AI background task rejected. chatId={}", context.chatId(), e);
            deliverAnswer(context, progressMessageId, formatMarkdownV2Response(prompt, "系统繁忙，请稍后重试。"));
        }
    }

    private void answerInBackground(BotContext context, String prompt, Integer progressMessageId) {
        String answer;
        try {
            answer = deepSeekChatService.chat(prompt);
        } catch (Exception e) {
            log.error("DeepSeek chat failed. chatId={}", context.chatId(), e);
            answer = "DeepSeek 调用失败，请检查 `spring.ai.openai.api-key`、`spring.ai.openai.base-url`、模型名和网络。";
        }
        if (answer == null || answer.isBlank()) {
            answer = "DeepSeek 没有返回可用内容。";
        }
        deliverAnswer(context, progressMessageId, formatMarkdownV2Response(prompt, answer));
    }

    /**
     * 有占位消息则原位编辑为最终答案，否则直接发送新消息。
     */
    private void deliverAnswer(BotContext context, Integer progressMessageId, String text) {
        if (progressMessageId == null) {
            reply(context, text);
            return;
        }
        messenger.editMessageText(context.chatId(), progressMessageId, text, "MarkdownV2");
        Integer commandMessageId = context.message() != null ? context.message().getMessageId() : null;
        scheduleAutoDelete(context.chatId(), commandMessageId, progressMessageId);
    }

    private Integer sendThinkingMessage(BotContext context) {
        return Replier.of(context, messenger).textAndReturnId(THINKING_TEXT);
    }

    private void reply(BotContext context, String text) {
        Integer replyMessageId = Replier.of(context, messenger).markdownV2AndReturnId(text);
        if (replyMessageId != null) {
            scheduleAutoDelete(context.chatId(), context.message().getMessageId(), replyMessageId);
        }
    }

    private void scheduleAutoDelete(Long chatId, Integer commandMessageId, Integer replyMessageId) {
        if (chatId == null || !botProperties.getAi().isAutoDeleteEnabled()) {
            return;
        }
        long delaySeconds = Math.max(1L, botProperties.getAi().getAutoDeleteDelaySeconds());
        scheduleDelete(chatId, commandMessageId, delaySeconds);
        scheduleDelete(chatId, replyMessageId, delaySeconds);
    }

    private void scheduleDelete(Long chatId, Integer messageId, long delaySeconds) {
        if (chatId == null || messageId == null) {
            return;
        }
        botScheduler.schedule(
                () -> messenger.deleteMessageSilently(chatId, messageId),
                Instant.now().plusSeconds(delaySeconds)
        );
    }

    public static String formatMarkdownV2Response(String prompt, String answer) {
        String normalizedAnswer = limit(normalizeForTelegram(answer), 3800);
        String safeAnswer = toMarkdownV2Quote(normalizedAnswer, shouldCollapseAnswer(normalizedAnswer));
        String safePrompt = toMarkdownV2Quote(limit(normalizeForTelegram(prompt), MAX_QUOTE_LENGTH));
        if (safePrompt.isBlank()) {
            return safeAnswer;
        }
        return safePrompt + "\n\n" + safeAnswer;
    }

    static String normalizeForTelegram(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private static String toMarkdownV2Quote(String value) {
        return toMarkdownV2Quote(value, false);
    }

    private static String toMarkdownV2Quote(String value, boolean expandable) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        String[] lines = value.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append('>').append(BotUtil.escapeMarkdownV2(lines[i]));
            if (expandable && i == lines.length - 1) {
                builder.append("||");
            }
        }
        return builder.toString();
    }

    private static boolean shouldCollapseAnswer(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.length() > EXPANDABLE_ANSWER_LENGTH || value.lines().count() > EXPANDABLE_ANSWER_LINES;
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    static String resolvePrompt(BotContext context) {
        String prompt = BotUtil.commandArgument(context == null ? null : context.text());
        if (!prompt.isBlank()) {
            return prompt;
        }
        return context == null ? "" : BotUtil.repliedMessageText(context.message());
    }
}
