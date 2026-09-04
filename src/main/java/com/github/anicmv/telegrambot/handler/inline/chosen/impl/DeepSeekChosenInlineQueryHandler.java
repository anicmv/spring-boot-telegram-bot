package com.github.anicmv.telegrambot.handler.inline.chosen.impl;

import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.handler.inline.chosen.ChosenInlineQueryResultHandler;
import com.github.anicmv.telegrambot.service.AiAccessControlService;
import com.github.anicmv.telegrambot.handler.command.impl.AiCommandHandler;
import com.github.anicmv.telegrambot.service.DeepSeekChatService;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/5/1 15:26
 * @description DeepSeek 已选内联结果处理器，异步将占位消息替换为模型回答。
 */
@BotInline(BotConstant.INLINE_ID_DEEPSEEK)
@Log4j2
@Component
public class DeepSeekChosenInlineQueryHandler implements ChosenInlineQueryResultHandler {

    private final Messenger messenger;
    private final AiAccessControlService aiAccessControlService;
    private final DeepSeekChatService deepSeekChatService;
    private final TaskExecutor botBackgroundExecutor;

    public DeepSeekChosenInlineQueryHandler(Messenger messenger,
                                            AiAccessControlService aiAccessControlService,
                                            DeepSeekChatService deepSeekChatService,
                                            @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor) {
        this.messenger = messenger;
        this.aiAccessControlService = aiAccessControlService;
        this.deepSeekChatService = deepSeekChatService;
        this.botBackgroundExecutor = botBackgroundExecutor;
    }

    @Override
    public void execute(BotContext context) {
        if (context.chosenInlineQuery().getInlineMessageId() == null || context.chosenInlineQuery().getInlineMessageId().isBlank()) {
            return;
        }
        botBackgroundExecutor.execute(() -> process(context));
    }

    private void process(BotContext context) {
        String inlineMessageId = context.chosenInlineQuery().getInlineMessageId();
        if (aiAccessControlService.isBlocked(context.userId())) {
            messenger.editInlineMessageText(
                    inlineMessageId,
                    AiCommandHandler.formatMarkdownV2Response(extractPrompt(context.text()), aiAccessControlService.blockedMessage()),
                    "MarkdownV2",
                    true
            );
            return;
        }
        String prompt = extractPrompt(context.text());
        try {
            String answer = deepSeekChatService.chat(prompt);
            if (answer == null || answer.isBlank()) {
                answer = "DeepSeek 没有返回可用内容。";
            }
            messenger.editInlineMessageText(
                    inlineMessageId,
                    AiCommandHandler.formatMarkdownV2Response(prompt, answer),
                    "MarkdownV2",
                    true
            );
        } catch (Exception e) {
            log.error("DeepSeek chosen inline processing failed. inlineMessageId={}", inlineMessageId, e);
            messenger.editInlineMessageText(
                    inlineMessageId,
                    AiCommandHandler.formatMarkdownV2Response(prompt, "DeepSeek 调用失败，请稍后重试。"),
                    "MarkdownV2",
                    true
            );
        }
    }

    private String extractPrompt(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String trimmed = query.trim();
        int firstBlank = trimmed.indexOf(' ');
        if (firstBlank < 0) {
            return "";
        }
        return trimmed.substring(firstBlank + 1).trim();
    }
}
