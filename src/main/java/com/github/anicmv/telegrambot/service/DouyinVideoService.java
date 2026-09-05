package com.github.anicmv.telegrambot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anicmv.telegrambot.utils.BotUtil;
import com.github.anicmv.telegrambot.utils.HttpUtil;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * @author anicmv
 * @date 2026/5/3 15:30
 * @description 抖音公开视频解析与下载服务。
 */
@Log4j2
@Service
public class DouyinVideoService {

    private static final String DETAIL_API = "https://www.douyin.com/aweme/v1/web/aweme/detail/";
    private static final String SHARE_VIDEO_URL = "https://www.iesdouyin.com/share/video/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36";
    private static final String MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148";
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://\\S+|v\\.douyin\\.com/\\S+|douyin\\.com/(?:video|note)/\\d+"
    );
    private static final Pattern PLAY_URL_PATTERN = Pattern.compile(
            "\"url_list\"\\s*:\\s*\\[\\s*\"(https:\\\\u002F\\\\u002F[^\"]*(?:playwm|play)[^\"]*)\"\\s*\\]"
    );
    private static final Pattern DESC_PATTERN = Pattern.compile("\"desc\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern AUTHOR_PATTERN = Pattern.compile("\"nickname\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern[] ID_PATTERNS = {
            Pattern.compile("/video/(\\d+)"),
            Pattern.compile("/aweme/detail/(\\d+)"),
            Pattern.compile("/note/(\\d+)"),
            Pattern.compile("video_id=(\\d+)"),
            Pattern.compile("aweme_id=(\\d+)"),
            Pattern.compile("note_id=(\\d+)"),
            Pattern.compile("\"aweme_id\"\\s*:\\s*\"(\\d+)\""),
            Pattern.compile("\"itemId\"\\s*:\\s*\"(\\d+)\"")
    };

    private final ObjectMapper objectMapper;
    private final String cookie;

    public DouyinVideoService(@Value("${bot.douyin.cookie:${DOUYIN_COOKIE:}}") String cookie) {
        this.objectMapper = new ObjectMapper();
        this.cookie = cookie == null ? "" : cookie.strip();
        log.info("Douyin cookie configured: {}", !this.cookie.isBlank());
    }

    public DownloadedVideo download(String text) {
        ResolvedVideo resolved = resolve(text);
        Path outputPath = createOutputPath(resolved.id(), resolved.desc());
        boolean downloaded = downloadVideoWithFallback(resolved.downloadUrl(), outputPath);
        if (!downloaded) {
            BotUtil.deleteQuietly(outputPath);
            throw new IllegalStateException("视频下载失败，请稍后重试。");
        }
        return new DownloadedVideo(
                resolved.id(),
                resolved.desc(),
                resolved.author(),
                resolved.sourceUrl(),
                resolved.realVideoUrl(),
                outputPath
        );
    }

    public ResolvedVideo resolve(String text) {
        String originalUrl = extractFirstUrl(text);
        log.info("抖音解析开始：输入文本={}, 提取链接={}", text, originalUrl);
        String videoId = resolveVideoId(originalUrl);
        if (videoId == null || videoId.isBlank()) {
            throw new IllegalArgumentException("无法提取视频 ID，请检查抖音链接是否正确。");
        }
        log.info("抖音解析到 videoId={}", videoId);

        JsonNode aweme = null;
        String source = "";
        JsonNode data = requestAwemeDetail(videoId, originalUrl);
        if (data != null && !data.path("aweme_detail").isMissingNode()) {
            aweme = data.path("aweme_detail");
            source = "detail_api";
            log.info("通过详情接口解析到视频信息。videoId={}", videoId);
        }
        if (aweme == null || aweme.isMissingNode() || aweme.isNull()) {
            aweme = requestPageAwemeDetail(videoId, originalUrl);
            if (aweme != null && !aweme.isMissingNode() && !aweme.isNull()) {
                source = "web_page_fallback";
                log.info("通过网页兜底解析到视频信息。videoId={}", videoId);
            }
        }
        if (aweme == null || aweme.isMissingNode() || aweme.isNull()) {
            aweme = requestMobileShareAwemeDetail(videoId);
            if (aweme != null && !aweme.isMissingNode() && !aweme.isNull()) {
                source = "mobile_share_fallback";
                log.info("通过移动分享页兜底解析到视频信息。videoId={}", videoId);
            }
        }
        if (aweme == null || aweme.isMissingNode() || aweme.isNull()) {
            if (cookie.isBlank()) {
                throw new IllegalStateException("无法获取视频详情，且未读取到 bot.douyin.cookie 或 DOUYIN_COOKIE。");
            }
            throw new IllegalStateException("已读取到抖音 Cookie，但仍未解析到视频信息，可能是 Cookie 失效、链接受限或触发风控。");
        }

        String videoUrl = bestVideoUrl(aweme);
        if (videoUrl == null || videoUrl.isBlank()) {
            throw new IllegalStateException("没有找到可下载的视频地址，当前链接可能是图集或权限受限。");
        }

        String id = firstText(videoId, aweme.path("aweme_id"), aweme.path("awemeId"), aweme.path("id"));
        String desc = textValue(aweme.path("desc"), "");
        String author = firstText("", aweme.path("author").path("nickname"), aweme.path("authorInfo").path("nickname"));
        log.info("抖音解析完成：source={}, videoId={}, author={}, realVideoUrl={}", source, id, author, videoUrl);
        return new ResolvedVideo(id, desc, author, originalUrl, videoUrl, videoUrl);
    }

    static String extractFirstUrl(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        String url = matcher.find() ? matcher.group(0) : text.strip();
        url = url.replaceAll("[，。！？、)）]+$", "");
        if (!url.startsWith("http")) {
            return "https://" + url;
        }
        return url;
    }

    String resolveVideoId(String url) {
        String videoId = extractVideoId(url);
        if (videoId != null) {
            log.info("链接中直接提取到 videoId={}。url={}", videoId, url);
            return videoId;
        }

        String currentUrl = url;
        for (int i = 0; i < 5; i++) {
            String redirectUrl = HttpUtil.redirectUrl(currentUrl, pageHeaders());
            if (redirectUrl == null || redirectUrl.isBlank()) {
                break;
            }
            currentUrl = resolveUrl(currentUrl, redirectUrl);
            log.info("短链跳转第{}步：{}", i + 1, currentUrl);
            videoId = extractVideoId(currentUrl);
            if (videoId != null) {
                log.info("重定向后提取到 videoId={}。", videoId);
                return videoId;
            }
        }

        String html = HttpUtil.get(currentUrl, pageHeaders());
        videoId = extractVideoId(html);
        if (videoId != null) {
            log.info("从页面 HTML 提取到 videoId={}。", videoId);
        }
        return videoId;
    }

    private JsonNode requestAwemeDetail(String videoId, String originalUrl) {
        String[] referers = originalUrl.contains("/note/")
                ? new String[]{"https://www.douyin.com/note/" + videoId, "https://www.douyin.com/video/" + videoId}
                : new String[]{"https://www.douyin.com/video/" + videoId};
        for (String referer : referers) {
            String response = HttpUtil.get(DETAIL_API + "?" + HttpUtil.buildQueryString(detailParams(videoId)),
                    apiHeaders(referer));
            if (response == null || response.isBlank()) {
                continue;
            }
            try {
                JsonNode data = objectMapper.readTree(response);
                if (!data.path("aweme_detail").isMissingNode() && !data.path("aweme_detail").isNull()) {
                    return data;
                }
                log.warn("Douyin detail API returned no aweme_detail. videoId={}, cookieConfigured={}, status_code={}, status_msg={}",
                        videoId, !cookie.isBlank(), data.path("status_code").asText(""), data.path("status_msg").asText(""));
            } catch (IOException e) {
                log.warn("Failed to parse Douyin detail API response. videoId={}", videoId, e);
                return null;
            }
        }
        return null;
    }

    private JsonNode requestPageAwemeDetail(String videoId, String originalUrl) {
        String[] pageUrls = originalUrl.contains("/note/")
                ? new String[]{"https://www.douyin.com/note/" + videoId, "https://www.douyin.com/video/" + videoId}
                : new String[]{"https://www.douyin.com/video/" + videoId};
        for (String pageUrl : pageUrls) {
            String html = HttpUtil.get(pageUrl, pageHeaders());
            JsonNode aweme = extractVideoNodeFromPageHtml(html, videoId);
            if (aweme != null) {
                return aweme;
            }
        }
        return null;
    }

    private JsonNode requestMobileShareAwemeDetail(String videoId) {
        String html = HttpUtil.get(SHARE_VIDEO_URL + videoId + "/", mobileShareHeaders());
        if (html == null || html.isBlank()) {
            return null;
        }
        Matcher playUrlMatcher = PLAY_URL_PATTERN.matcher(html);
        if (!playUrlMatcher.find()) {
            return null;
        }
        String playUrl = cleanVideoUrl(decodeJsonEscaped(playUrlMatcher.group(1)));
        if (playUrl == null || playUrl.isBlank()) {
            return null;
        }

        String desc = "";
        Matcher descMatcher = DESC_PATTERN.matcher(html);
        if (descMatcher.find()) {
            desc = decodeJsonEscaped(descMatcher.group(1));
        }
        String author = "";
        Matcher authorMatcher = AUTHOR_PATTERN.matcher(html);
        if (authorMatcher.find()) {
            author = decodeJsonEscaped(authorMatcher.group(1));
        }

        com.fasterxml.jackson.databind.node.ObjectNode aweme = objectMapper.createObjectNode();
        aweme.put("aweme_id", videoId);
        aweme.put("desc", desc);
        com.fasterxml.jackson.databind.node.ObjectNode authorNode = objectMapper.createObjectNode();
        authorNode.put("nickname", author);
        aweme.set("author", authorNode);
        com.fasterxml.jackson.databind.node.ObjectNode playAddrNode = objectMapper.createObjectNode();
        playAddrNode.set("url_list", objectMapper.createArrayNode().add(playUrl));
        com.fasterxml.jackson.databind.node.ObjectNode videoNode = objectMapper.createObjectNode();
        videoNode.set("play_addr", playAddrNode);
        aweme.set("video", videoNode);
        return aweme;
    }

    private JsonNode extractVideoNodeFromPageHtml(String html, String videoId) {
        if (html == null || html.isBlank()) {
            return null;
        }
        JsonNode routerData = extractJsonScript(html, "RENDER_DATA");
        JsonNode aweme = findVideoNode(routerData, videoId);
        if (aweme != null) {
            return aweme;
        }
        JsonNode universalData = extractJsonScript(html, "UNIVERSAL_DATA_FOR_REHYDRATION");
        aweme = findVideoNode(universalData, videoId);
        if (aweme != null) {
            return aweme;
        }
        return findVideoUrlNode(html);
    }

    private JsonNode extractJsonScript(String html, String scriptId) {
        Pattern pattern = Pattern.compile(
                "<script[^>]*id=[\"']" + Pattern.quote(scriptId) + "[\"'][^>]*>(.*?)</script>",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        String json = matcher.group(1);
        if ("RENDER_DATA".equals(scriptId)) {
            json = java.net.URLDecoder.decode(json, java.nio.charset.StandardCharsets.UTF_8);
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            log.warn("Failed to parse Douyin page script. scriptId={}", scriptId, e);
            return null;
        }
    }

    private JsonNode findVideoNode(JsonNode node, String videoId) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            String awemeId = firstText("", node.path("aweme_id"), node.path("awemeId"), node.path("id"));
            if (videoId.equals(awemeId) && hasVideoUrl(node)) {
                return node;
            }
            if (awemeId.isBlank() && hasVideoUrl(node)) {
                return node;
            }
        }
        for (JsonNode child : node) {
            JsonNode found = findVideoNode(child, videoId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private JsonNode findVideoUrlNode(String html) {
        Matcher matcher = Pattern.compile(
                "(https?:\\\\?/\\\\?/[^\"'\\\\]+(?:douyinpic|douyinvod|byteimg|snssdk)[^\"'\\\\]+)",
                Pattern.CASE_INSENSITIVE
        ).matcher(html);
        while (matcher.find()) {
            String url = matcher.group(1).replace("\\/", "/");
            if (url.contains("play") || url.contains(".mp4")) {
                return objectMapper.createObjectNode()
                        .put("aweme_id", "")
                        .set("video", objectMapper.createObjectNode()
                                .set("play_addr", objectMapper.createObjectNode()
                                        .set("url_list", objectMapper.createArrayNode().add(url))));
            }
        }
        return null;
    }

    private static Map<String, String> detailParams(String videoId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("device_platform", "webapp");
        params.put("aid", "6383");
        params.put("channel", "channel_pc_web");
        params.put("aweme_id", videoId);
        params.put("pc_client_type", "1");
        params.put("version_code", "290100");
        params.put("version_name", "29.1.0");
        params.put("cookie_enabled", "true");
        params.put("browser_language", "zh-CN");
        params.put("browser_platform", "Win32");
        params.put("browser_name", "Chrome");
        params.put("browser_version", "130.0.0.0");
        params.put("browser_online", "true");
        params.put("engine_name", "Blink");
        params.put("engine_version", "130.0.0.0");
        params.put("os_name", "Windows");
        params.put("os_version", "10");
        params.put("platform", "PC");
        params.put("msToken", "");
        return params;
    }

    private static String bestVideoUrl(JsonNode aweme) {
        JsonNode video = firstNode(aweme.path("video"), aweme.path("videoInfo"));
        JsonNode bestBitRate = StreamSupport.stream(firstNode(video.path("bit_rate"), video.path("bitRate")).spliterator(), false)
                .filter(JsonNode::isObject)
                .filter(item -> firstUrl(firstNode(item.path("play_addr"), item.path("playAddr"))) != null)
                .max(Comparator.comparingInt(item -> firstNode(item.path("bit_rate"), item.path("bitRate")).asInt(0)))
                .orElse(null);
        if (bestBitRate != null) {
            return cleanVideoUrl(firstUrl(firstNode(bestBitRate.path("play_addr"), bestBitRate.path("playAddr"))));
        }

        JsonNode playAddr = firstNode(video.path("play_addr"), video.path("playAddr"), video.path("playApi"));
        String playUrl = firstUrl(playAddr);
        if (playUrl != null) {
            return cleanVideoUrl(playUrl);
        }

        String videoUri = textValue(playAddr.path("uri"), "");
        if (!videoUri.isBlank()) {
            return "https://aweme.snssdk.com/aweme/v1/play/?video_id="
                    + HttpUtil.urlEncode(videoUri) + "&ratio=1080p&line=0";
        }
        return null;
    }

    private static boolean hasVideoUrl(JsonNode node) {
        JsonNode video = firstNode(node.path("video"), node.path("videoInfo"), node);
        JsonNode playAddr = firstNode(video.path("play_addr"), video.path("playAddr"), video.path("playApi"));
        return firstUrl(playAddr) != null;
    }

    private static String firstUrl(JsonNode playAddr) {
        JsonNode urlList = firstNode(playAddr.path("url_list"), playAddr.path("urlList"));
        if (!urlList.isArray() || urlList.isEmpty()) {
            return null;
        }
        return textValue(urlList.get(0), "");
    }

    private static String cleanVideoUrl(String url) {
        if (url == null) {
            return null;
        }
        String cleaned = url.replace("playwm", "play");
        if (!cleaned.contains("/aweme/v1/play")) {
            return cleaned;
        }
        String nestedVideoUrl = extractNestedVideoUrl(cleaned);
        if (nestedVideoUrl != null && !nestedVideoUrl.isBlank()) {
            return nestedVideoUrl.replace("playwm", "play");
        }
        String promoted = cleaned;
        if (promoted.contains("ratio=")) {
            promoted = promoted.replaceAll("([?&]ratio=)[^&]*", "$11080p");
        } else {
            promoted = promoted + (promoted.contains("?") ? "&" : "?") + "ratio=1080p";
        }
        return promoted;
    }

    private static String extractNestedVideoUrl(String awemePlayUrl) {
        try {
            URI uri = URI.create(awemePlayUrl);
            String rawQuery = uri.getRawQuery();
            if (rawQuery == null || rawQuery.isBlank()) {
                return null;
            }
            for (String pair : rawQuery.split("&")) {
                if (!pair.startsWith("video_id=")) {
                    continue;
                }
                String rawValue = pair.substring("video_id=".length());
                if (rawValue.isBlank()) {
                    return null;
                }
                String decoded = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
                return (decoded.startsWith("http://") || decoded.startsWith("https://")) ? decoded : null;
            }
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String extractVideoId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (Pattern pattern : ID_PATTERNS) {
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String resolveUrl(String baseUrl, String location) {
        try {
            return URI.create(baseUrl).resolve(location).toString();
        } catch (IllegalArgumentException e) {
            return location;
        }
    }

    private static Path createOutputPath(String id, String desc) {
        try {
            Path tempDir = Files.createTempDirectory("douyin-");
            return tempDir.resolve(safeFilename(desc, id) + ".mp4");
        } catch (IOException e) {
            throw new IllegalStateException("创建临时下载目录失败。", e);
        }
    }

    private static String safeFilename(String text, String fallback) {
        String safeText = (text == null ? "" : text).replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        if (safeText.isBlank()) {
            return fallback;
        }
        return safeText.substring(0, Math.min(80, safeText.length())).strip();
    }

    private Map<String, String> apiHeaders(String referer) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", referer);
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Cache-Control", "no-cache");
        headers.put("Pragma", "no-cache");
        headers.put("Sec-Fetch-Dest", "empty");
        headers.put("Sec-Fetch-Mode", "cors");
        headers.put("Sec-Fetch-Site", "same-origin");
        headers.put("X-Requested-With", "XMLHttpRequest");
        if (!cookie.isBlank()) {
            headers.put("Cookie", cookie);
        }
        return headers;
    }

    private Map<String, String> pageHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Referer", "https://www.douyin.com/");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Cache-Control", "no-cache");
        headers.put("Pragma", "no-cache");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "same-origin");
        if (!cookie.isBlank()) {
            headers.put("Cookie", cookie);
        }
        return headers;
    }

    private static Map<String, String> mobileShareHeaders() {
        return Map.of(
                "User-Agent", MOBILE_USER_AGENT,
                "Referer", "https://www.iesdouyin.com/",
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8"
        );
    }

    private boolean downloadVideoWithFallback(String videoUrl, Path outputPath) {
        Set<String> candidateUrls = new LinkedHashSet<>();
        if (videoUrl != null && !videoUrl.isBlank()) {
            candidateUrls.add(videoUrl);
            if (videoUrl.contains("/play/")) {
                candidateUrls.add(videoUrl.replace("/play/", "/playwm/"));
            }
            if (videoUrl.contains("/playwm/")) {
                candidateUrls.add(videoUrl.replace("/playwm/", "/play/"));
            }
        }

        log.info("开始下载抖音视频，候选地址数={}", candidateUrls.size());
        for (String candidate : candidateUrls) {
            String resolved = HttpUtil.redirectUrl(candidate, mobileDownloadHeaders());
            String finalUrl = (resolved == null || resolved.isBlank()) ? candidate : resolveUrl(candidate, resolved);
            List<Map<String, String>> headerCandidates = headersForDownload(finalUrl);
            log.info("尝试下载候选地址: {}, 请求头策略数={}", finalUrl, headerCandidates.size());
            for (Map<String, String> headers : headerCandidates) {
                if (HttpUtil.downloadToFile(finalUrl, headers, outputPath)) {
                    log.info("抖音视频下载成功: {}", finalUrl);
                    return true;
                }
            }
        }
        log.warn("抖音视频下载失败：所有候选地址和请求头策略均未成功。");
        return false;
    }

    private static Map<String, String> downloadHeaders() {
        return Map.of(
                "User-Agent", USER_AGENT,
                "Referer", "https://www.douyin.com/",
                "Origin", "https://www.douyin.com",
                "Range", "bytes=0-"
        );
    }

    private static Map<String, String> mobileDownloadHeaders() {
        return Map.of(
                "User-Agent", MOBILE_USER_AGENT,
                "Referer", "https://www.iesdouyin.com/",
                "Range", "bytes=0-"
        );
    }

    private static List<Map<String, String>> headersForDownload(String url) {
        List<Map<String, String>> headers = new ArrayList<>();
        // douyinvod CDN 对 Referer/Origin 敏感，实测仅 UA 更稳定。
        if (isDouyinVodUrl(url)) {
            headers.add(Map.of("User-Agent", MOBILE_USER_AGENT));
            return headers;
        }
        headers.add(downloadHeaders());
        headers.add(mobileDownloadHeaders());
        headers.add(Map.of("User-Agent", MOBILE_USER_AGENT));
        return headers;
    }

    private static boolean isDouyinVodUrl(String url) {
        return url != null && url.contains("douyinvod.com");
    }

    private static String textValue(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        return node.asText(fallback);
    }

    private String decodeJsonEscaped(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            return objectMapper.readValue("\"" + raw + "\"", String.class);
        } catch (IOException e) {
            return raw.replace("\\u002F", "/").replace("\\/", "/");
        }
    }

    private static String firstText(String fallback, JsonNode... nodes) {
        for (JsonNode node : nodes) {
            String value = textValue(node, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private static JsonNode firstNode(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    public record DownloadedVideo(String id, String desc, String author, String sourceUrl, String realVideoUrl, Path path) {
    }

    public record ResolvedVideo(String id, String desc, String author, String sourceUrl, String realVideoUrl, String downloadUrl) {
    }
}
