package com.github.anicmv.telegrambot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anicmv.telegrambot.utils.HttpUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SauceNAO 以图搜图服务。
 * API 文档：https://saucenao.com/info.php
 */
@Log4j2
@Service
public class SauceNaoService {

    private static final String SAUCENAO_API = "https://saucenao.com/search.php";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private final String apiKey;
    private final ObjectMapper objectMapper;

    public SauceNaoService(ObjectMapper objectMapper, @Value("${bot.saucenao.api-key:}") String apiKey) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    public record SearchResult(
            String title,
            String author,
            String platform,
            String sourceUrl,
            String similarity,
            String thumbnailUrl,
            String sourceType
    ) {
    }

    public record SearchResponse(boolean success, String message, List<SearchResult> results) {
    }

    public SearchResponse searchByUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return new SearchResponse(false, "图片链接为空。", List.of());
        }

        try {
            String html = HttpUtil.get(SAUCENAO_API + "?" + buildQueryString(imageUrl), saucenaoHeaders());
            return parseHtmlResponse(html);
        } catch (Exception e) {
            log.warn("SauceNAO 搜索失败。url={}", imageUrl, e);
            return new SearchResponse(false, "搜索失败：" + e.getMessage(), List.of());
        }
    }

    private String buildQueryString(String imageUrl) {
        StringBuilder qs = new StringBuilder();
        qs.append("url=").append(HttpUtil.urlEncode(imageUrl));
        if (!apiKey.isBlank()) {
            qs.append("&api_key=").append(apiKey);
        }
        qs.append("&output_type=2"); // JSON output
        qs.append("&numres=5");
        qs.append("&hide=0"); // Don't hide results with low similarity
        return qs.toString();
    }

    private SearchResponse parseHtmlResponse(String html) {
        if (html == null || html.isBlank()) {
            return new SearchResponse(false, "SauceNAO 返回空内容。", List.of());
        }

        try {
            // Extract JSON from <script> tag or direct JSON response
            String jsonStr = extractJson(html);
            if (jsonStr == null) {
                return parseLegacyHtml(html);
            }

            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                return new SearchResponse(false, "未找到相似图片。", List.of());
            }

            List<SearchResult> searchResults = new ArrayList<>();
            AtomicInteger count = new AtomicInteger(0);
            for (JsonNode item : results) {
                if (count.get() >= 5) break;

                JsonNode data = item.path("data");
                JsonNode header = item.path("header");

                String similarity = header.path("similarity").asText("");
                double sim = 0;
                try { sim = Double.parseDouble(similarity); } catch (Exception ignored) {}
                if (sim < 50) continue; // 低于 50% 直接过滤

                String title = data.path("title").asText("");
                String author = data.path("creator").isArray()
                        ? data.path("creator").iterator().next().asText("")
                        : data.path("creator").asText("");
                String platform = data.path("index_name").asText("");
                String sourceUrl = data.path("source_url").asText("");
                String thumbnail = header.path("thumbnail").asText("");
                String sourceType = data.path("ext_url") != null ? "image" : "video";

                searchResults.add(new SearchResult(title, author, platform, sourceUrl, similarity, thumbnail, sourceType));
                count.incrementAndGet();
            }

            if (searchResults.isEmpty()) {
                return new SearchResponse(true, "未找到相似度高于 50% 的结果。", List.of());
            }

            return new SearchResponse(true, "找到 " + searchResults.size() + " 个结果", searchResults);
        } catch (IOException e) {
            log.warn("SauceNAO JSON 解析失败，尝试 HTML 解析。", e);
            return parseLegacyHtml(html);
        }
    }

    private String extractJson(String html) {
        int jsonStart = html.indexOf("<!-- --></div><script>document.getElementById('dissimilarity')");
        if (jsonStart == -1) {
            // Try finding JSON in script tags
            int scriptStart = html.indexOf("<script>");
            int scriptEnd = html.indexOf("</script>", scriptStart);
            if (scriptStart != -1 && scriptEnd != -1) {
                String script = html.substring(scriptStart + "<script>".length(), scriptEnd);
                if (script.contains("results")) {
                    return script;
                }
            }
            return null;
        }
        // Legacy HTML parsing fallback
        return null;
    }

    private SearchResponse parseLegacyHtml(String html) {
        // Simple regex-based HTML parsing fallback
        List<SearchResult> results = new ArrayList<>();
        try {
            java.util.regex.Pattern resultPat = java.util.regex.Pattern.compile(
                    "<div class=\\\"result\\\"[^>]*>(.*?)</div>\\s*<div class=\\\"resultfooter\\\"",
                    java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher resultMatcher = resultPat.matcher(html);

            int count = 0;
            while (resultMatcher.find() && count < 5) {
                String block = resultMatcher.group(1);

                String similarity = "";
                java.util.regex.Matcher simMatcher = java.util.regex.Pattern.compile(
                        "class=\\\"result相似度[^>]*>\\s*([\\d.]+)%?").matcher(block);
                if (simMatcher.find()) similarity = simMatcher.group(1);

                double sim = 0;
                try { sim = Double.parseDouble(similarity); } catch (Exception ignored) {}
                if (sim < 50) continue;

                String title = "", author = "", platform = "", sourceUrl = "", thumbnail = "";
                java.util.regex.Matcher m;

                m = java.util.regex.Pattern.compile("class=\\\"resulttitle\\\"[^>]*>\\s*<[^>]*>\\s*([^<]+)").matcher(block);
                if (m.find()) title = m.group(1).strip();

                m = java.util.regex.Pattern.compile("class=\\\"resultmix\\\"[^>]*>\\s*<[^>]*?>\\s*([^<]+)").matcher(block);
                if (m.find()) author = m.group(1).strip();

                m = java.util.regex.Pattern.compile("class=\\\"resultindex\\\"[^>]*>\\s*([^<]+)").matcher(block);
                if (m.find()) platform = m.group(1).strip();

                m = java.util.regex.Pattern.compile("class=\\\"resultimage\\\"[^>]*?>\\s*<img src=\\\"([^\"]+)\\\"").matcher(block);
                if (m.find()) thumbnail = m.group(1);

                m = java.util.regex.Pattern.compile("href=\\\"(https?://[^\"]+)\\\"[^>]*>\\s*Source").matcher(block);
                if (m.find()) sourceUrl = m.group(1);

                results.add(new SearchResult(title, author, platform, sourceUrl, similarity, thumbnail, "unknown"));
                count++;
            }
        } catch (Exception e) {
            log.warn("SauceNAO HTML 解析异常。", e);
        }

        if (results.isEmpty()) {
            return new SearchResponse(false, "解析结果失败，请检查图片链接是否可访问。", List.of());
        }
        return new SearchResponse(true, "找到 " + results.size() + " 个结果（仅显示相似度 >50%）", results);
    }

    private java.util.Map<String, String> saucenaoHeaders() {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", "https://saucenao.com/");
        headers.put("Accept", "text/html,application/xhtml+xml");
        return headers;
    }
}
