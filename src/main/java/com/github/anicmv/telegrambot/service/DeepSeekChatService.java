package com.github.anicmv.telegrambot.service;

import com.github.anicmv.telegrambot.config.BotProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * @author anicmv
 * @date 2026/5/1 17:10
 * @description DeepSeek 对话服务，封装 Spring AI 调用。
 */
@Service
public class DeepSeekChatService {

    private final ChatClient chatClient;
    private final BotProperties botProperties;

    public DeepSeekChatService(ChatClient.Builder chatClientBuilder, BotProperties botProperties) {
        this.chatClient = chatClientBuilder.build();
        this.botProperties = botProperties;
    }

    public String chat(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "请输入问题，例如：/ai 帮我总结今天的工作。";
        }
        String content = chatClient.prompt()
                .system(botProperties.getAi().getSystemPrompt())
                .user(userMessage.trim())
                .call()
                .content();
        if (content == null || content.isBlank()) {
            return "DeepSeek 没有返回可用内容。";
        }
        return content.trim();
    }

    /**
     * 使用自定义系统提示词对话，供画像分析等场景使用；
     * 与 {@link #chat(String)} 隔离，不复用机器人人设提示词。
     */
    public String chat(String systemPrompt, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "";
        }
        String content = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage.trim())
                .call()
                .content();
        return content == null ? "" : content.trim();
    }
}
