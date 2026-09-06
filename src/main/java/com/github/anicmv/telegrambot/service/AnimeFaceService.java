package com.github.anicmv.telegrambot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

/**
 * AnimTrace 动漫/Gal 人物识别服务。
 */
@Log4j2
@Service
public class AnimeFaceService {

    private static final String API_URL = "https://api.animetrace.com/v1/search";
    private static final String MODEL = "animetrace-yuri-4.2";
    private static final MediaType JPEG = MediaType.get("image/jpeg");
    private static final int MAX_BYTES = 49 * 1024 * 1024;
    private static final int MAX_PERSONS = 10;
    private static final int MAX_CANDIDATES = 5;
    private static final int MAX_RESULT_LENGTH = 3500;

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public AnimeFaceService(ObjectMapper objectMapper, OkHttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public SearchResponse search(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return new SearchResponse(false, "图片内容为空", "", false, List.of());
        }
        if (imageBytes.length > MAX_BYTES) {
            return new SearchResponse(false, "图片超过大小限制", "", false, List.of());
        }
        RequestBody fileBody = RequestBody.create(imageBytes, JPEG);
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", MODEL)
                .addFormDataPart("is_multi", "1")
                .addFormDataPart("ai_detect", "1")
                .addFormDataPart("file", "image.jpg", fileBody)
                .build();
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 TelegramBot")
                .post(body)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String text = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                log.warn("AnimTrace 请求失败: status={}, body={}", response.code(), abbreviate(text));
                return new SearchResponse(false, "识别服务暂时不可用，请稍后重试", "", false, List.of());
            }
            return parseResponse(text);
        } catch (IOException e) {
            log.warn("AnimTrace 请求异常", e);
            return new SearchResponse(false, "识别服务请求失败，请稍后重试", "", false, List.of());
        }
    }

    SearchResponse parseResponse(String json) {
        if (json == null || json.isBlank()) {
            return new SearchResponse(false, "识别服务返回空内容", "", false, List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            int code = root.path("code").asInt(-1);
            String traceId = root.path("trace_id").asText("");
            if (code != 0 && code != 200 && code != 17720) {
                return new SearchResponse(false, errorMessage(code), traceId,
                        root.path("ai").asBoolean(false), List.of());
            }
            List<PersonResult> persons = new ArrayList<>();
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode person : data) {
                    if (persons.size() >= MAX_PERSONS) {
                        break;
                    }
                    List<Candidate> candidates = new ArrayList<>();
                    JsonNode characters = person.path("character");
                    if (characters.isArray()) {
                        for (JsonNode candidate : characters) {
                            if (candidates.size() >= MAX_CANDIDATES) {
                                break;
                            }
                            String work = candidate.path("work").asText("").strip();
                            String character = candidate.path("character").asText("").strip();
                            if (!work.isBlank() || !character.isBlank()) {
                                candidates.add(new Candidate(work, character));
                            }
                        }
                    }
                    if (!candidates.isEmpty()) {
                        persons.add(new PersonResult(person.path("not_confident").asBoolean(false), candidates));
                    }
                }
            }
            if (persons.isEmpty()) {
                return new SearchResponse(true, "未识别到动漫人物", traceId,
                        root.path("ai").asBoolean(false), List.of());
            }
            return new SearchResponse(true, "识别成功", traceId,
                    root.path("ai").asBoolean(false), persons);
        } catch (Exception e) {
            log.warn("AnimTrace 响应解析失败: body={}", abbreviate(json), e);
            return new SearchResponse(false, "识别结果解析失败，请稍后重试", "", false, List.of());
        }
    }

    public String formatHtml(SearchResponse response) {
        return formatHtml(response, Map.of(), Map.of());
    }

    public String formatHtml(SearchResponse response,
                             Map<String, BangumiWorkTranslationService.Translation> workTranslations,
                             Map<String, BangumiWorkTranslationService.Translation> characterTranslations) {
        Map<String, BangumiWorkTranslationService.Translation> translations =
                workTranslations == null ? Map.of() : workTranslations;
        Map<String, BangumiWorkTranslationService.Translation> characterTranslationMap =
                characterTranslations == null ? Map.of() : characterTranslations;
        StringBuilder result = new StringBuilder("<b>🎭 动漫/Gal 人物识别</b>\n");
        if (response.ai()) {
            result.append("⚠️ 疑似 AI 生成图片\n");
        }
        for (PersonResult person : response.persons()) {
            result.append('\n');
            if (person.notConfident()) {
                result.append("⚠️ 候选结果仅供参考\n");
            }
            for (Candidate candidate : person.candidates()) {
                String characterKey = BangumiWorkTranslationService.characterKey(
                        normalizeWork(candidate.work()), normalizeWork(candidate.character()));
                result.append("• ")
                        .append(displayWithTranslation(candidate.character(),
                                characterTranslationMap.get(characterKey)))
                        .append(" ｜ ")
                        .append(displayWithTranslation(candidate.work(),
                                translations.get(normalizeWork(candidate.work()))))
                        .append('\n');
            }
        }
        if (result.length() > MAX_RESULT_LENGTH) {
            result.setLength(MAX_RESULT_LENGTH - 3);
            result.append("...");
        }
        return result.toString();
    }

    private String errorMessage(int code) {
        return switch (code) {
            case 17701 -> "图片超过大小限制";
            case 17702, 17731 -> "识别服务繁忙，请稍后重试";
            case 17704 -> "识别服务维护中，请稍后重试";
            case 17705 -> "图片格式不支持";
            case 17708 -> "图片中的人物数量超过限制";
            case 17728 -> "识别服务已达到本次使用上限";
            default -> "识别失败，请稍后重试（错误码 " + code + "）";
        };
    }

    private String displayWithTranslation(String original,
                                          BangumiWorkTranslationService.Translation translation) {
        if (translation == null || translation.nameCn() == null || translation.nameCn().isBlank()) {
            return escape(original);
        }
        return translation.toHtmlLink() + "（" + escape(original) + "）";
    }

    private String escape(String text) {
        return com.github.anicmv.telegrambot.utils.BotUtil.escapeHtml(text);
    }

    static String normalizeWork(String value) {
        return BangumiWorkTranslationService.normalizeWork(value);
    }

    private String abbreviate(String text) {
        if (text == null) return "null";
        return text.length() <= 500 ? text : text.substring(0, 497) + "...";
    }

    public record Candidate(String work, String character) {
    }

    public record PersonResult(boolean notConfident, List<Candidate> candidates) {
    }

    public record SearchResponse(boolean success, String message, String traceId, boolean ai,
                                 List<PersonResult> persons) {
    }
}
