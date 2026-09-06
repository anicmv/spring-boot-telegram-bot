package com.github.anicmv.telegrambot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

class BangumiWorkTranslationServiceTest {

    @Test
    void shouldSendFilterAsObjectWithArrayTypeWhenQueryingSubject() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    requestBody.set(readBody(chain));
                    return fakeResponse(chain, "{\"data\":[]}");
                })
                .build();
        BangumiWorkTranslationService service =
                new BangumiWorkTranslationService(new ObjectMapper(), client);

        service.querySubject("らき☆すた");

        String json = requestBody.get();
        assertTrue(json.contains("\"filter\":{\"type\":[2]}"), "实际请求体: " + json);
    }

    @Test
    void shouldReturnChineseNameAndSubjectIdWhenSubjectMatchesExactly() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> fakeResponse(chain, """
                        {"data":[{"id":276,"name":"らき☆すた","name_cn":"幸运星"}]}"""))
                .build();
        BangumiWorkTranslationService service =
                new BangumiWorkTranslationService(new ObjectMapper(), client);

        assertEquals(new BangumiWorkTranslationService.SubjectMatch("幸运星", 276),
                service.querySubject("らき☆すた"));
    }

    @Test
    void shouldReturnNullWhenNoSubjectMatchesExactly() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> fakeResponse(chain, """
                        {"data":[{"id":1,"name":"其他作品","name_cn":"别的"}]}"""))
                .build();
        BangumiWorkTranslationService service =
                new BangumiWorkTranslationService(new ObjectMapper(), client);

        assertNull(service.querySubject("らき☆すた"));
    }

    @Test
    void shouldReadChineseNameFromInfoboxWhenParsingCharacterDetail() throws IOException {
        BangumiWorkTranslationService service =
                new BangumiWorkTranslationService(new ObjectMapper(), new OkHttpClient());

        assertEquals("泉此方", service.parseCharacterChineseName("""
                {"name":"泉こなた","infobox":[
                  {"key":"别名","value":[{"v":"こなちゃん"}]},
                  {"key":"简体中文名","value":"泉此方"}]}"""));
        assertNull(service.parseCharacterChineseName("{\"name\":\"泉こなた\",\"infobox\":[]}"));
    }

    @Test
    void shouldTranslateRawTitlesDedupedByNormalizedForm() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> fakeResponse(chain, """
                        {"data":[{"id":276,"name":"らき☆すた","name_cn":"幸运星"}]}"""))
                .build();
        BangumiWorkTranslationService service =
                new BangumiWorkTranslationService(new ObjectMapper(), client);

        var translations = service.translateTitlesAsync(
                java.util.Arrays.asList("らき☆すた", " らき☆すた ", "", null, "无此作品")).join();

        var expected = new BangumiWorkTranslationService.Translation(
                "幸运星", "https://bgm.tv/subject/276");
        assertEquals(expected, translations.get("らき☆すた"));
        assertEquals(expected, translations.get(" らき☆すた "));
        assertEquals(2, translations.size());
    }

    @Test
    void shouldTranslateWorkAndCharacterThroughSubjectChain() {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    String path = chain.request().url().encodedPath();
                    String body = switch (path) {
                        case "/v0/search/subjects" -> """
                                {"data":[{"id":276,"name":"らき☆すた","name_cn":"幸运星"}]}""";
                        case "/v0/subjects/276/characters" -> """
                                [{"id":275,"name":"泉こなた"},{"id":1667,"name":"柊かがみ"}]""";
                        case "/v0/characters/275" -> """
                                {"name":"泉こなた","infobox":[{"key":"简体中文名","value":"泉此方"}]}""";
                        default -> "{}";
                    };
                    return fakeResponse(chain, body);
                })
                .build();
        BangumiWorkTranslationService service =
                new BangumiWorkTranslationService(new ObjectMapper(), client);
        AnimeFaceService.SearchResponse response = new AnimeFaceService.SearchResponse(
                true, "识别成功", "trace-1", false,
                List.of(new AnimeFaceService.PersonResult(false, List.of(
                        new AnimeFaceService.Candidate("らき☆すた", "泉こなた"),
                        new AnimeFaceService.Candidate("らき☆すた", "柊かがみ")))));

        BangumiWorkTranslationService.TranslationResult result =
                service.translateAsync(response).join();

        assertEquals(new BangumiWorkTranslationService.Translation(
                        "幸运星", "https://bgm.tv/subject/276"),
                result.workTranslations().get("らき☆すた"));
        assertEquals(new BangumiWorkTranslationService.Translation(
                        "泉此方", "https://bgm.tv/character/275"),
                result.characterTranslations().get(
                        BangumiWorkTranslationService.characterKey("らき☆すた", "泉こなた")));
        assertNull(result.characterTranslations().get(
                BangumiWorkTranslationService.characterKey("らき☆すた", "柊かがみ")));
    }

    private static String readBody(Interceptor.Chain chain) throws IOException {
        okio.Buffer buffer = new okio.Buffer();
        chain.request().body().writeTo(buffer);
        return buffer.readUtf8();
    }

    private static Response fakeResponse(Interceptor.Chain chain, String body) {
        return new Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(body, MediaType.get("application/json; charset=utf-8")))
                .build();
    }
}
