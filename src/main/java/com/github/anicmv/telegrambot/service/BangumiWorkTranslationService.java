package com.github.anicmv.telegrambot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anicmv.telegrambot.utils.BotUtil;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * 使用 Bangumi v0 查询动画作品与角色的中文名。
 * 作品：search/subjects 精确匹配 name 取 name_cn 与条目 id；
 * 角色：subjects/{id}/characters 用原文人名匹配取角色 id，再查 characters/{id} 的 infobox 中文名。
 */
@Log4j2
@Service
public class BangumiWorkTranslationService implements AutoCloseable {

    private static final String SUBJECT_SEARCH_URL =
            "https://api.bgm.tv/v0/search/subjects?limit=5&offset=0";
    private static final String SUBJECT_CHARACTERS_URL =
            "https://api.bgm.tv/v0/subjects/%d/characters";
    private static final String CHARACTER_DETAIL_URL =
            "https://api.bgm.tv/v0/characters/%d";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String USER_AGENT = "anicmv/spring-boot-telegram-bot/1.0 (https://github.com/anicmv/spring-boot-telegram-bot)";
    private static final int MAX_WORKS = 8;
    private static final int MAX_CHARACTERS = 12;

    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bangumi-translation-");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, SubjectMatch> subjectCache = new ConcurrentHashMap<>();
    private final Map<Integer, List<CharacterRef>> charactersCache = new ConcurrentHashMap<>();
    private final Map<Integer, String> characterNameCache = new ConcurrentHashMap<>();

    @Autowired
    public BangumiWorkTranslationService(ObjectMapper objectMapper, OkHttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /**
     * 翻译任意标题列表（如 /anime 的 TraceMoe 标题）。
     * 返回 Map 以传入的原始字符串为 key，value 为匹配到的简体中文名（含条目链接）；相同归一化标题只查一次。
     */
    public CompletableFuture<Map<String, Translation>> translateTitlesAsync(List<String> rawTitles) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Translation> translations = new LinkedHashMap<>();
            Map<String, SubjectMatch> byNormalized = new HashMap<>();
            int queried = 0;
            for (String raw : rawTitles) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String key = normalizeWork(raw);
                if (key.isBlank()) {
                    continue;
                }
                if (!byNormalized.containsKey(key)) {
                    if (queried >= MAX_WORKS) {
                        continue;
                    }
                    queried++;
                    byNormalized.put(key, subjectCache.computeIfAbsent(key, this::querySubject));
                }
                translations.put(raw, toTranslation(byNormalized.get(key)));
            }
            return withoutMisses(translations);
        }, executor);
    }

    private static Translation toTranslation(SubjectMatch match) {
        if (match == null || match.nameCn().isBlank()) {
            return null;
        }
        return new Translation(match.nameCn(), subjectUrl(match.subjectId()));
    }

    private static Map<String, Translation> withoutMisses(Map<String, Translation> translations) {
        translations.values().removeIf(Objects::isNull);
        return translations;
    }

    public CompletableFuture<TranslationResult> translateAsync(AnimeFaceService.SearchResponse response) {
        List<String> works = response.persons().stream()
                .flatMap(person -> person.candidates().stream())
                .map(AnimeFaceService.Candidate::work)
                .filter(work -> work != null && !work.isBlank())
                .map(BangumiWorkTranslationService::normalizeWork)
                .distinct()
                .limit(MAX_WORKS)
                .toList();
        List<CharacterPair> pairs = response.persons().stream()
                .flatMap(person -> person.candidates().stream())
                .filter(candidate -> candidate.work() != null && !candidate.work().isBlank()
                        && candidate.character() != null && !candidate.character().isBlank())
                .map(candidate -> new CharacterPair(
                        normalizeWork(candidate.work()), normalizeWork(candidate.character())))
                .distinct()
                .limit(MAX_CHARACTERS)
                .toList();
        log.info("Bangumi 翻译任务提交: traceId={}, works={}, characters={}",
                response.traceId(), works, pairs.stream().map(CharacterPair::character).toList());

        return CompletableFuture.supplyAsync(() -> translate(works, pairs), executor);
    }

    private TranslationResult translate(List<String> works, List<CharacterPair> pairs) {
        Map<String, SubjectMatch> subjects = new LinkedHashMap<>();
        Map<String, Translation> workTranslations = new LinkedHashMap<>();
        for (String work : works) {
            SubjectMatch match = subjectCache.computeIfAbsent(work, this::querySubject);
            if (match == null) {
                continue;
            }
            subjects.put(work, match);
            Translation translation = toTranslation(match);
            if (translation != null) {
                workTranslations.put(work, translation);
            }
        }

        Map<String, Translation> characterTranslations = new LinkedHashMap<>();
        for (CharacterPair pair : pairs) {
            SubjectMatch subject = subjects.get(pair.work());
            if (subject == null || subject.subjectId() <= 0) {
                continue;
            }
            int characterId = findCharacterId(subject.subjectId(), pair.character());
            if (characterId <= 0) {
                continue;
            }
            String nameCn = characterNameCache.computeIfAbsent(
                    characterId, this::queryCharacterChineseName);
            if (nameCn != null && !nameCn.isBlank()) {
                characterTranslations.put(characterKey(pair.work(), pair.character()),
                        new Translation(nameCn, characterUrl(characterId)));
            }
        }
        return new TranslationResult(workTranslations, characterTranslations);
    }

    SubjectMatch querySubject(String work) {
        log.info("Bangumi 查询开始: work={}", work);
        try {
            String requestJson = objectMapper.createObjectNode()
                    .put("keyword", work)
                    .put("sort", "match")
                    .set("filter", objectMapper.createObjectNode()
                            .set("type", objectMapper.createArrayNode().add(2)))
                    .toString();
            Request request = new Request.Builder()
                    .url(SUBJECT_SEARCH_URL)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .post(RequestBody.create(requestJson, JSON))
                    .build();
            String body = execute(request, "work=" + work);
            if (body == null) {
                return null;
            }
            SubjectMatch match = parseSubjectMatch(work, body);
            if (match == null) {
                log.warn("Bangumi 未匹配到条目: work={}, response={}", work, abbreviate(body));
            } else {
                log.info("Bangumi 匹配成功: work={}, nameCn={}, subjectId={}",
                        work, match.nameCn(), match.subjectId());
            }
            return match;
        } catch (IOException e) {
            log.warn("Bangumi 查询失败: work={}", work, e);
            return null;
        }
    }

    private int findCharacterId(int subjectId, String character) {
        for (CharacterRef ref : charactersCache.computeIfAbsent(subjectId, this::loadCharacters)) {
            if (normalizeWork(character).equals(normalizeWork(ref.name()))) {
                return ref.characterId();
            }
        }
        return 0;
    }

    private List<CharacterRef> loadCharacters(int subjectId) {
        Request request = new Request.Builder()
                .url(SUBJECT_CHARACTERS_URL.formatted(subjectId))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build();
        String body = execute(request, "subjectId=" + subjectId);
        if (body == null) {
            return List.of();
        }
        try {
            List<CharacterRef> refs = new ArrayList<>();
            for (JsonNode item : objectMapper.readTree(body)) {
                refs.add(new CharacterRef(item.path("name").asText(""), item.path("id").asInt(0)));
            }
            return List.copyOf(refs);
        } catch (IOException e) {
            log.warn("Bangumi 角色列表解析失败: subjectId={}", subjectId, e);
            return List.of();
        }
    }

    String queryCharacterChineseName(int characterId) {
        Request request = new Request.Builder()
                .url(CHARACTER_DETAIL_URL.formatted(characterId))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .get()
                .build();
        String body = execute(request, "characterId=" + characterId);
        if (body == null) {
            return null;
        }
        try {
            return parseCharacterChineseName(body);
        } catch (IOException e) {
            log.warn("Bangumi 角色详情解析失败: characterId={}", characterId, e);
            return null;
        }
    }

    String parseCharacterChineseName(String response) throws IOException {
        for (JsonNode item : objectMapper.readTree(response).path("infobox")) {
            String key = item.path("key").asText("");
            if (key.equals("简体中文名") || key.equals("中文名")) {
                String value = item.path("value").asText("").strip();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private SubjectMatch parseSubjectMatch(String work, String response) throws IOException {
        JsonNode data = objectMapper.readTree(response).path("data");
        if (!data.isArray()) {
            return null;
        }
        String normalized = normalizeWork(work);
        for (JsonNode subject : data) {
            if (normalized.equals(normalizeWork(subject.path("name").asText("")))
                    && subject.path("id").asInt(0) > 0) {
                return new SubjectMatch(
                        subject.path("name_cn").asText("").strip(), subject.path("id").asInt(0));
            }
        }
        return null;
    }

    private String execute(Request request, String logContext) {
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("Bangumi 查询失败: {}, status={}, bodyPresent={}",
                        logContext, response.code(), response.body() != null);
                return null;
            }
            return response.body().string();
        } catch (IOException e) {
            log.warn("Bangumi 查询失败: {}", logContext, e);
            return null;
        }
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "null";
        }
        return text.length() <= 1000 ? text : text.substring(0, 997) + "...";
    }

    static String normalizeWork(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip()
                .replaceAll("\\s+", " ");
    }

    public static String characterKey(String normalizedWork, String normalizedCharacter) {
        return normalizedWork + "\u0001" + normalizedCharacter;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    public record TranslationResult(Map<String, Translation> workTranslations,
                                    Map<String, Translation> characterTranslations) {
    }

    /** 简体中文名 + Bangumi 条目链接。 */
    public record Translation(String nameCn, String url) {

        /** HTML 片段：可点击中文名（查不到条目时退化为纯文本）。 */
        public String toHtmlLink() {
            String name = BotUtil.escapeHtml(nameCn);
            return url == null || url.isBlank()
                    ? name
                    : "<a href=\"" + BotUtil.escapeHtml(url) + "\">" + name + "</a>";
        }
    }

    static String subjectUrl(int subjectId) {
        return "https://bgm.tv/subject/" + subjectId;
    }

    static String characterUrl(int characterId) {
        return "https://bgm.tv/character/" + characterId;
    }

    record SubjectMatch(String nameCn, int subjectId) {
    }

    record CharacterRef(String name, int characterId) {
    }

    record CharacterPair(String work, String character) {
    }
}
