package com.github.anicmv.telegrambot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.log4j.Log4j2;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 使用 Bangumi v0 查询动画作品的中文名。角色名保留 AnimTrace 原文。
 */
@Log4j2
@Service
public class BangumiWorkTranslationService implements AutoCloseable {

    private static final String API_URL = "https://api.bgm.tv/v0/search/subjects?limit=5&offset=0";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String USER_AGENT = "anicmv/spring-boot-telegram-bot/1.0 (https://github.com/anicmv/spring-boot-telegram-bot)";
    private static final int MAX_WORKS = 8;

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bangumi-translation-");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Autowired
    public BangumiWorkTranslationService(ObjectMapper objectMapper, OkHttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public CompletableFuture<Map<String, String>> translateAsync(AnimeFaceService.SearchResponse response) {
        LinkedHashMap<String, String> translations = new LinkedHashMap<>();
        response.persons().stream()
                .flatMap(person -> person.candidates().stream())
                .map(AnimeFaceService.Candidate::work)
                .filter(work -> work != null && !work.isBlank())
                .map(BangumiWorkTranslationService::normalizeWork)
                .distinct()
                .limit(MAX_WORKS)
                .forEach(key -> translations.put(key, null));

        return CompletableFuture.supplyAsync(() -> {
            translations.replaceAll((work, ignored) -> cache.computeIfAbsent(work, this::queryChineseName));
            return translations.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        }, executor);
    }

    String queryChineseName(String work) {
        try {
            String requestJson = objectMapper.createObjectNode()
                    .put("keyword", work)
                    .set("filter", objectMapper.createObjectNode().putArray("type").add(2))
                    .toString();
            Request request = new Request.Builder()
                    .url(API_URL)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .post(RequestBody.create(requestJson, JSON))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return null;
                }
                return parseChineseName(work, response.body().string());
            }
        } catch (IOException e) {
            log.warn("Bangumi 查询失败: work={}", work, e);
            return null;
        }
    }

    String parseChineseName(String work, String response) throws IOException {
        JsonNode data = objectMapper.readTree(response).path("data");
        if (!data.isArray()) {
            return null;
        }
        String normalized = normalizeWork(work);
        for (JsonNode subject : data) {
            String name = subject.path("name").asText("");
            String nameCn = subject.path("name_cn").asText("").strip();
            if (!nameCn.isBlank() && normalized.equals(normalizeWork(name))) {
                return nameCn;
            }
        }
        return null;
    }

    static String normalizeWork(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip()
                .replaceAll("\\s+", " ");
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
