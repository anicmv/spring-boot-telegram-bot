package com.github.anicmv.telegrambot.service;

import com.github.anicmv.telegrambot.config.BotProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

/**
 * @author anicmv
 * @date 2026/5/1 17:10
 * @description AI 对话服务，封装 Spring AI 调用，模型由 spring.ai.openai 配置决定。
 */
@Service
public class AiChatService {

    /**
     * 百炼 OpenAI 兼容模式的联网搜索开关（body 顶层参数）。
     */
    static final String ENABLE_SEARCH = "enable_search";

    private final ChatClient chatClient;
    private final OpenAiChatModel chatModel;
    private final BotProperties botProperties;

    public AiChatService(ChatClient.Builder chatClientBuilder,
                               OpenAiChatModel chatModel,
                               BotProperties botProperties) {
        this.chatClient = chatClientBuilder.build();
        this.chatModel = chatModel;
        this.botProperties = botProperties;
    }

    /**
     * /ai 与内联对话链路：默认开启百炼联网搜索；画像等内部链路走
     * {@link #chatWithUsage(String, String)}，不受影响。
     */
    public String chat(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "请输入问题，例如：/ai 帮我总结今天的工作。";
        }
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .system(botProperties.getAi().getSystemPrompt())
                .user(userMessage.trim());
        if (botProperties.getAi().isWebSearchEnabled()) {
            spec = spec.options(webSearchOptions(chatModel.getOptions()));
        }
        String content = spec.call().content();
        if (content == null || content.isBlank()) {
            return "模型没有返回可用内容。";
        }
        return content.trim();
    }

    /**
     * 基于模型默认 options 复制出按请求覆盖项，仅追加 enable_search，
     * 保留 model、extraBody（如 enable_thinking）等既有配置。
     */
    static OpenAiChatOptions.Builder webSearchOptions(OpenAiChatOptions defaults) {
        Map<String, Object> extraBody = new LinkedHashMap<>();
        if (defaults.getExtraBody() != null) {
            extraBody.putAll(defaults.getExtraBody());
        }
        extraBody.put(ENABLE_SEARCH, true);
        OpenAiChatOptions.Builder builder = defaults.mutate();
        builder.extraBody(extraBody);
        return builder;
    }

    /**
     * 使用自定义系统提示词对话，供画像分析等场景使用；
     * 与 {@link #chat(String)} 隔离，不复用机器人人设提示词。
     */
    public String chat(String systemPrompt, String userMessage) {
        return chatWithUsage(systemPrompt, userMessage).content();
    }

    /**
     * 同 {@link #chat(String, String)}，额外返回本轮消耗 token 总数，供画像成本统计。
     */
    public ChatResult chatWithUsage(String systemPrompt, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return new ChatResult("", 0L);
        }
        ChatResponse response = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage.trim())
                .call()
                .chatResponse();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return new ChatResult("", 0L);
        }
        String content = response.getResult().getOutput().getText();
        long totalTokens = response.getMetadata() != null && response.getMetadata().getUsage() != null
                ? response.getMetadata().getUsage().getTotalTokens() : 0L;
        return new ChatResult(content == null ? "" : content.trim(), totalTokens);
    }

    /**
     * @param content    模型输出内容
     * @param totalTokens 本轮消耗 token 总数（含输入输出，取不到时为 0）
     */
    public record ChatResult(String content, Long totalTokens) {
    }
}
