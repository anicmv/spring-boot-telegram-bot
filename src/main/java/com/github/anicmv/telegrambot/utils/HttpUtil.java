package com.github.anicmv.telegrambot.utils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import lombok.extern.log4j.Log4j2;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description HTTP 同步工具类（基于 JDK HttpClient）。
 * 所有方法失败时返回 null/false，不抛异常；非 2xx 响应视为失败。
 */
@Log4j2
public final class HttpUtil {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final HttpClient CLIENT = newClient(HttpClient.Redirect.NORMAL);
    private static final HttpClient NO_REDIRECT_CLIENT = newClient(HttpClient.Redirect.NEVER);

    private HttpUtil() {
    }

    private static HttpClient newClient(HttpClient.Redirect redirect) {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(redirect)
                .build();
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
            joiner.add(urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()));
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
        return send(url, headers, null, null, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public static byte[] getBytes(String url, Map<String, String> headers) {
        HttpResponse<byte[]> response = sendRaw(url, headers, null, null, HttpResponse.BodyHandlers.ofByteArray(), "GET bytes");
        return is2xx(statusCode(response)) ? response.body() : null;
    }

    public static boolean downloadToFile(String url, Map<String, String> headers, Path targetPath) {
        if (isBlank(url) || targetPath == null) {
            return false;
        }
        HttpResponse<Path> response = sendRaw(url, headers, null, null,
                HttpResponse.BodyHandlers.ofFile(targetPath), "下载");
        if (response == null) {
            return false; // 网络异常已由 sendRaw 记录
        }
        int status = response.statusCode();
        if (is2xx(status)) {
            return true;
        }
        if (status == 403) {
            log.info("HTTP 下载被目标站点拒绝(403)，将由上层决定是否重试。url={}", url);
        } else {
            log.warn("HTTP 下载失败: url={}, targetPath={}, status={}", url, targetPath, status);
        }
        return false;
    }

    public static String postJson(String url, String jsonBody) {
        return postJson(url, jsonBody, null);
    }

    public static String postJson(String url, String jsonBody, Map<String, String> headers) {
        return send(url, headers, "application/json",
                jsonBody == null ? "" : jsonBody, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public static String postForm(String url, Map<String, String> formData, Map<String, String> headers) {
        return send(url, headers, "application/x-www-form-urlencoded",
                buildQueryString(formData), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 探测 3xx 跳转目标（不跟随重定向）；无跳转或失败返回 null。
     */
    public static String redirectUrl(String url, Map<String, String> headers) {
        if (isBlank(url)) {
            return null;
        }
        try {
            HttpResponse<Void> response = NO_REDIRECT_CLIENT.send(
                    requestBuilder(url, headers, null).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                return response.headers().firstValue("Location").orElse(null);
            }
            return null;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("HTTP redirect probe failed: url={}, errorType={}", url, e.getClass().getSimpleName(), e);
            return null;
        }
    }

    // ==================== 私有 ====================

    private static <T> T send(String url, Map<String, String> headers, String contentType,
                              String body, HttpResponse.BodyHandler<T> bodyHandler) {
        HttpResponse<T> response = sendRaw(url, headers, contentType, body, bodyHandler, url);
        return is2xx(statusCode(response)) ? response.body() : null;
    }

    private static <T> HttpResponse<T> sendRaw(String url, Map<String, String> headers, String contentType,
                                               String body, HttpResponse.BodyHandler<T> bodyHandler, String logTag) {
        if (isBlank(url)) {
            return null;
        }
        try {
            HttpRequest request = requestBuilder(url, headers, contentType)
                    .method(body == null ? "GET" : "POST", body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            return CLIENT.send(request, bodyHandler);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("HTTP request failed: tag={}, url={}, errorType={}", logTag, url, e.getClass().getSimpleName(), e);
            return null;
        }
    }

    private static HttpRequest.Builder requestBuilder(String url, Map<String, String> headers, String contentType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(DEFAULT_TIMEOUT);
        if (contentType != null) {
            builder.setHeader("Content-Type", contentType);
        }
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (value != null) {
                    builder.setHeader(key, value);
                }
            });
        }
        return builder;
    }

    private static int statusCode(HttpResponse<?> response) {
        return response == null ? -1 : response.statusCode();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
