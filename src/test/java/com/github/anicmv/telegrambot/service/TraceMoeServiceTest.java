package com.github.anicmv.telegrambot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceMoeServiceTest {

    private final TraceMoeService service = new TraceMoeService(new ObjectMapper(), new OkHttpClient());

    @Test
    void shouldParseSuccessfulResponseWhenErrorIsEmpty() {
        String json = """
                {
                  "error": "",
                  "result": [{
                    "anilist": {
                      "id": 20954,
                      "title": {
                        "romaji": "Koe no Katachi",
                        "native": "聲の形",
                        "english": "A Silent Voice"
                      }
                    },
                    "episode": null,
                    "from": 123.4,
                    "similarity": 0.95,
                    "video": "https://media.trace.moe/video.mp4",
                    "image": "https://media.trace.moe/image.jpg"
                  }]
                }
                """;

        TraceMoeService.SearchResponse response = service.parseResponse(json);

        assertTrue(response.success());
        assertEquals(1, response.results().size());
        TraceMoeService.AnimeResult result = response.results().getFirst();
        assertEquals(20954, result.anilistId());
        assertEquals("Koe no Katachi", result.title());
        assertEquals("聲の形", result.titleNative());
        assertEquals("A Silent Voice", result.titleEnglish());
        assertEquals("未知集数", result.episode());
        assertEquals("02:03", result.formatTimestamp());
        assertEquals("95.00%", result.formatSimilarity());
        assertEquals("https://media.trace.moe/video.mp4", result.previewUrl());
        assertEquals("https://media.trace.moe/image.jpg", result.imageUrl());
    }

    @Test
    void shouldReturnApiErrorOnlyWhenErrorIsNotEmpty() {
        TraceMoeService.SearchResponse response = service.parseResponse("""
                {"error":"Invalid image","result":[]}
                """);

        assertFalse(response.success());
        assertEquals("API 错误：Invalid image", response.message());
        assertTrue(response.results().isEmpty());
    }

    @Test
    void shouldFilterLowSimilarityResults() {
        TraceMoeService.SearchResponse response = service.parseResponse("""
                {
                  "error": "",
                  "result": [{
                    "anilist": {"id": 1, "title": {"romaji": "Unknown"}},
                    "episode": 1,
                    "from": 0,
                    "similarity": 0.42,
                    "video": "",
                    "image": ""
                  }]
                }
                """);

        assertTrue(response.success());
        assertEquals("未找到相似度高于 80% 的结果", response.message());
        assertTrue(response.results().isEmpty());
    }

    @Test
    void shouldSortBySimilarityBeforeLimitingResults() {
        TraceMoeService.SearchResponse response = service.parseResponse("""
                {
                  "error": "",
                  "result": [
                    {"anilist":{"id":1,"title":{"romaji":"A"}},"similarity":0.81},
                    {"anilist":{"id":2,"title":{"romaji":"B"}},"similarity":0.99},
                    {"anilist":{"id":3,"title":{"romaji":"C"}},"similarity":0.90},
                    {"anilist":{"id":4,"title":{"romaji":"D"}},"similarity":0.95}
                  ]
                }
                """);

        assertTrue(response.success());
        assertEquals(3, response.results().size());
        assertEquals(2, response.results().get(0).anilistId());
        assertEquals(4, response.results().get(1).anilistId());
        assertEquals(3, response.results().get(2).anilistId());
    }

    @Test
    void shouldDownloadPreviewToTemporaryMp4() throws Exception {
        byte[] video = "preview-video".getBytes(StandardCharsets.UTF_8);
        TraceMoeService downloadService = serviceWithResponse(200, video);

        Path preview = downloadService.downloadPreview("https://media.trace.moe/preview.mp4");
        try {
            assertTrue(preview.getFileName().toString().startsWith("trace-moe-preview-"));
            assertTrue(preview.getFileName().toString().endsWith(".mp4"));
            assertArrayEquals(video, Files.readAllBytes(preview));
        } finally {
            Files.deleteIfExists(preview);
        }
    }

    @Test
    void shouldRejectFailedOrEmptyPreviewResponse() {
        TraceMoeService failedService = serviceWithResponse(503, "unavailable".getBytes(StandardCharsets.UTF_8));
        TraceMoeService emptyService = serviceWithResponse(200, new byte[0]);

        assertThrows(IllegalStateException.class,
                () -> failedService.downloadPreview("https://media.trace.moe/preview.mp4"));
        assertThrows(IllegalStateException.class,
                () -> emptyService.downloadPreview("https://media.trace.moe/preview.mp4"));
    }

    private TraceMoeService serviceWithResponse(int status, byte[] body) {
        Interceptor interceptor = chain -> response(chain, status, body);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .build();
        return new TraceMoeService(new ObjectMapper(), client);
    }

    private Response response(Interceptor.Chain chain, int status, byte[] body) throws IOException {
        return new Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message(status >= 200 && status < 300 ? "OK" : "Error")
                .body(ResponseBody.create(body, MediaType.get("video/mp4")))
                .build();
    }
}
