package com.github.anicmv.telegrambot.utils;

import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description HTTP 相关工具类（基于 WebClient）。
 */
@Log4j2
public final class HttpUtil {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_TEXT_BUFFER_SIZE = 4 * 1024 * 1024;
    private static final int DEFAULT_BYTE_BUFFER_SIZE = 16 * 1024 * 1024;

    private static final WebClient WEB_CLIENT = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(
                    HttpClient.create()
                            .compress(true)
                            .followRedirect(true)
            ))
            .exchangeStrategies(ExchangeStrategies.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(DEFAULT_TEXT_BUFFER_SIZE))
                    .build())
            .build();

    private static final WebClient BYTE_WEB_CLIENT = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(
                    HttpClient.create()
                            .compress(true)
                            .followRedirect(true)
            ))
            .exchangeStrategies(ExchangeStrategies.builder()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(DEFAULT_BYTE_BUFFER_SIZE))
                    .build())
            .build();

    private static final WebClient NO_REDIRECT_WEB_CLIENT = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(
                    HttpClient.create()
                            .compress(true)
                            .followRedirect(false)
            ))
            .build();

    private HttpUtil() {
    }

    public static boolean is2xx(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    public static String buildQueryString(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = urlEncode(entry.getKey());
            String value = urlEncode(entry.getValue());
            joiner.add(key + "=" + value);
        }
        return joiner.toString();
    }

    public static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String get(String url) {
        return get(url, null);
    }

    public static String get(String url, Map<String, String> headers) {
        if (isBlank(url)) {
            return null;
        }
        return WEB_CLIENT.get()
                .uri(url)
                .headers(httpHeaders -> addHeaders(httpHeaders, headers))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(DEFAULT_TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn("HTTP GET failed: url={}, errorType={}", url, ex.getClass().getSimpleName(), ex);
                    return Mono.empty();
                })
                .block();
    }

    public static byte[] getBytes(String url, Map<String, String> headers) {
        if (isBlank(url)) {
            return null;
        }
        return BYTE_WEB_CLIENT.get()
                .uri(url)
                .headers(httpHeaders -> addHeaders(httpHeaders, headers))
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(DEFAULT_TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn("HTTP GET bytes failed: url={}, errorType={}", url, ex.getClass().getSimpleName(), ex);
                    return Mono.empty();
                })
                .block();
    }

    public static InputStream getInputStream(String url, Map<String, String> headers) {
        byte[] bytes = getBytes(url, headers);
        return bytes == null ? null : new ByteArrayInputStream(bytes);
    }

    public static boolean downloadToFile(String url, Map<String, String> headers, Path targetPath) {
        if (isBlank(url) || targetPath == null) {
            return false;
        }
        Boolean success = WEB_CLIENT.get()
                .uri(url)
                .headers(httpHeaders -> addHeaders(httpHeaders, headers))
                .retrieve()
                .bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class)
                .as(dataBuffers -> DataBufferUtils.write(
                        dataBuffers,
                        targetPath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                ))
                .thenReturn(true)
                .timeout(DEFAULT_TIMEOUT)
                .onErrorResume(ex -> {
                    if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException.Forbidden) {
                        log.info("HTTP 下载被目标站点拒绝(403)，将由上层决定是否重试。url={}", url);
                        return Mono.just(false);
                    }
                    log.warn("HTTP 下载失败: url={}, targetPath={}, errorType={}, message={}",
                            url, targetPath, ex.getClass().getSimpleName(), ex.getMessage());
                    return Mono.just(false);
                })
                .block();
        return Boolean.TRUE.equals(success);
    }

    public static String postJson(String url, String jsonBody) {
        return postJson(url, jsonBody, null);
    }

    public static String postJson(String url, String jsonBody, Map<String, String> headers) {
        if (isBlank(url)) {
            return null;
        }
        return WEB_CLIENT.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> addHeaders(httpHeaders, headers))
                .bodyValue(jsonBody == null ? "" : jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(DEFAULT_TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn("HTTP POST json failed: url={}, errorType={}", url, ex.getClass().getSimpleName(), ex);
                    return Mono.empty();
                })
                .block();
    }

    public static String postForm(String url, Map<String, String> formData, Map<String, String> headers) {
        if (isBlank(url)) {
            return null;
        }
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        if (formData != null) {
            body.setAll(formData);
        }
        return WEB_CLIENT.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(httpHeaders -> addHeaders(httpHeaders, headers))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(DEFAULT_TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn("HTTP POST form failed: url={}, errorType={}", url, ex.getClass().getSimpleName(), ex);
                    return Mono.empty();
                })
                .block();
    }

    public static String redirectUrl(String url, Map<String, String> headers) {
        if (isBlank(url)) {
            return null;
        }
        return NO_REDIRECT_WEB_CLIENT.get()
                .uri(url)
                .headers(httpHeaders -> addHeaders(httpHeaders, headers))
                .exchangeToMono(response -> {
                    int code = response.statusCode().value();
                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        return Mono.justOrEmpty(response.headers().asHttpHeaders().getFirst(HttpHeaders.LOCATION));
                    }
                    return Mono.empty();
                })
                .timeout(DEFAULT_TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn("HTTP redirect probe failed: url={}, errorType={}", url, ex.getClass().getSimpleName(), ex);
                    return Mono.empty();
                })
                .block();
    }

    private static void addHeaders(HttpHeaders target, Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        Map<String, String> safeHeaders = new HashMap<>(source);
        safeHeaders.values().removeIf(v -> v == null);
        target.setAll(safeHeaders);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
