package com.github.anicmv.telegrambot.service;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiChatServiceTest {

    @Test
    void webSearchOptionsShouldAppendEnableSearchAndKeepDefaultExtraBody() {
        OpenAiChatOptions defaults = OpenAiChatOptions.builder()
                .model("qwen3.8-flash")
                .extraBody(Map.of("enable_thinking", false))
                .build();

        OpenAiChatOptions override = AiChatService.webSearchOptions(defaults).build();

        assertEquals("qwen3.8-flash", override.getModel());
        Map<String, Object> extraBody = override.getExtraBody();
        assertEquals(Boolean.TRUE, extraBody.get("enable_search"));
        assertEquals(Boolean.FALSE, extraBody.get("enable_thinking"));
    }

    @Test
    void webSearchOptionsShouldTolerateMissingDefaultExtraBody() {
        OpenAiChatOptions defaults = OpenAiChatOptions.builder().model("x").build();

        OpenAiChatOptions override = AiChatService.webSearchOptions(defaults).build();

        assertTrue(override.getExtraBody().containsKey("enable_search"));
    }

    @Test
    void webSearchOptionsShouldNotMutateDefaults() {
        OpenAiChatOptions defaults = OpenAiChatOptions.builder()
                .extraBody(Map.of("enable_thinking", false))
                .build();

        AiChatService.webSearchOptions(defaults).build();

        assertEquals(Map.of("enable_thinking", false), defaults.getExtraBody());
    }
}
