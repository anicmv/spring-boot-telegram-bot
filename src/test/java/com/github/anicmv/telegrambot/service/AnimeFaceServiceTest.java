package com.github.anicmv.telegrambot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimeFaceServiceTest {

    private final AnimeFaceService service = new AnimeFaceService(new ObjectMapper(), new OkHttpClient());

    @Test
    void shouldParseMultiplePeopleAndCandidates() {
        String json = """
                {
                  "code": 0,
                  "ai": true,
                  "trace_id": "trace-1",
                  "data": [
                    {"not_confident": true, "character": [
                      {"work":"Clover Day's", "character":"鷹倉杏鈴"},
                      {"work":"作品二", "character":"角色二"}
                    ]},
                    {"not_confident": false, "character": [
                      {"work":"作品三", "character":"角色三"}
                    ]}
                  ]
                }
                """;

        AnimeFaceService.SearchResponse result = service.parseResponse(json);

        assertTrue(result.success());
        assertTrue(result.ai());
        assertEquals("trace-1", result.traceId());
        assertEquals(2, result.persons().size());
        assertEquals(2, result.persons().getFirst().candidates().size());
        assertTrue(service.formatHtml(result).contains("候选结果仅供参考"));
    }

    @Test
    void shouldHandleErrorAndEmptyResults() {
        AnimeFaceService.SearchResponse error = service.parseResponse("{\"code\":17705}");
        AnimeFaceService.SearchResponse empty = service.parseResponse("{\"code\":0,\"data\":[]}");

        assertFalse(error.success());
        assertEquals("图片格式不支持", error.message());
        assertTrue(empty.success());
        assertEquals("未识别到动漫人物", empty.message());
    }

    @Test
    void shouldRejectEmptyInput() {
        AnimeFaceService.SearchResponse result = service.search(new byte[0]);

        assertFalse(result.success());
        assertEquals("图片内容为空", result.message());
    }
}
