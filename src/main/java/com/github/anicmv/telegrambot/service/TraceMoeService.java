package com.github.anicmv.telegrambot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

/**
 * @author anicmv
 * @date 2026/9/5
 * @description trace.moe 动漫识图服务，通过上传图片识别动漫场景。
 * API 文档：https://soruly.github.io/trace.moe-api/
 */
@Log4j2
@Service
public class TraceMoeService {

    private static final String API_URL = "https://api.trace.moe/search?anilistInfo&cutBorders";
    private static final MediaType JPEG = MediaType.get("image/jpeg");
    private static final String USER_AGENT = "Mozilla/5.0 TelegramBot";
    private static final long MAX_PREVIEW_BYTES = 49L * 1024 * 1024;
    private static final double MIN_SIMILARITY = 0.8;
    private static final int MAX_RESULTS = 3;

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public TraceMoeService(ObjectMapper objectMapper, OkHttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public record AnimeResult(
            int anilistId,
            String title,
            String titleNative,
            String titleEnglish,
            String episode,
            double timestamp,
            double similarity,
            String previewUrl,
            String imageUrl
    ) {
        public String formatTimestamp() {
            int minutes = (int) (timestamp / 60);
            int seconds = (int) (timestamp % 60);
            return String.format("%02d:%02d", minutes, seconds);
        }

        public String formatSimilarity() {
            return String.format("%.2f%%", similarity * 100);
        }
    }

    public record SearchResponse(boolean success, String message, List<AnimeResult> results) {
    }

    public SearchResponse search(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return new SearchResponse(false, "图片内容为空", List.of());
        }

        RequestBody imageBody = RequestBody.create(imageBytes, JPEG);
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "telegram-image.jpg", imageBody)
                .build();
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                log.warn("trace.moe 请求失败: status={}, body={}", response.code(), abbreviate(responseBody));
                return new SearchResponse(false, "trace.moe 请求失败（HTTP " + response.code() + "）", List.of());
            }
            return parseResponse(responseBody);
        } catch (IOException e) {
            log.warn("trace.moe 搜索失败", e);
            return new SearchResponse(false, "搜索失败：" + e.getMessage(), List.of());
        }
    }

    public Path downloadPreview(String previewUrl) {
        if (previewUrl == null || previewUrl.isBlank()) {
            throw new IllegalArgumentException("预览片段地址为空");
        }

        Path previewFile = null;
        boolean downloaded = false;
        try {
            Request request = new Request.Builder()
                    .url(previewUrl)
                    .header("Accept", "video/mp4,video/*,*/*")
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://trace.moe/")
                    .get()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("HTTP " + response.code());
                }
                long contentLength = response.body().contentLength();
                if (contentLength > MAX_PREVIEW_BYTES) {
                    throw new IOException("预览片段超过大小限制");
                }
                previewFile = Files.createTempFile("trace-moe-preview-", ".mp4");
                try (InputStream input = response.body().byteStream();
                     OutputStream output = Files.newOutputStream(previewFile)) {
                    copyPreview(input, output);
                }
                if (Files.size(previewFile) == 0) {
                    throw new IOException("响应内容为空");
                }
                downloaded = true;
                return previewFile;
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("trace.moe 预览片段下载失败", e);
        } finally {
            if (!downloaded && previewFile != null) {
                try {
                    Files.deleteIfExists(previewFile);
                } catch (IOException e) {
                    log.warn("清理 trace.moe 预览临时文件失败: path={}", previewFile, e);
                }
            }
        }
    }

    SearchResponse parseResponse(String json) {
        if (json == null || json.isBlank()) {
            return new SearchResponse(false, "trace.moe 返回空内容", List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            String error = root.path("error").asText("").strip();
            if (!error.isEmpty()) {
                return new SearchResponse(false, "API 错误：" + error, List.of());
            }

            JsonNode resultArray = root.path("result");
            if (!resultArray.isArray() || resultArray.isEmpty()) {
                return new SearchResponse(false, "未找到匹配的动漫", List.of());
            }

            List<AnimeResult> results = new ArrayList<>();
            for (JsonNode item : resultArray) {
                double similarity = item.path("similarity").asDouble(0);
                if (similarity < MIN_SIMILARITY) {
                    continue;
                }

                JsonNode anilistNode = item.path("anilist");
                int anilistId = anilistNode.isObject()
                        ? anilistNode.path("id").asInt(0)
                        : anilistNode.asInt(0);
                JsonNode titleNode = anilistNode.path("title");
                results.add(new AnimeResult(
                        anilistId,
                        titleNode.path("romaji").asText(""),
                        titleNode.path("native").asText(""),
                        titleNode.path("english").asText(""),
                        formatEpisode(item.path("episode")),
                        item.path("from").asDouble(0),
                        similarity,
                        item.path("video").asText(""),
                        item.path("image").asText("")
                ));
            }

            results.sort(Comparator.comparingDouble(AnimeResult::similarity).reversed());
            if (results.size() > MAX_RESULTS) {
                results = new ArrayList<>(results.subList(0, MAX_RESULTS));
            }
            if (results.isEmpty()) {
                return new SearchResponse(true, "未找到相似度高于 80% 的结果", List.of());
            }
            return new SearchResponse(true, "找到 " + results.size() + " 个结果", results);
        } catch (Exception e) {
            log.warn("trace.moe 响应解析失败: body={}", abbreviate(json), e);
            return new SearchResponse(false, "trace.moe 响应解析失败", List.of());
        }
    }

    private void copyPreview(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        long totalBytes = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (totalBytes + read > MAX_PREVIEW_BYTES) {
                throw new IOException("预览片段超过大小限制");
            }
            output.write(buffer, 0, read);
            totalBytes += read;
        }
    }

    private String formatEpisode(JsonNode episodeNode) {
        if (episodeNode == null || episodeNode.isMissingNode() || episodeNode.isNull()) {
            return "未知集数";
        }
        String episode = episodeNode.asText("").strip();
        return episode.isEmpty() ? "未知集数" : "第 " + episode + " 集";
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= 500 ? text : text.substring(0, 497) + "...";
    }
}
