package com.github.anicmv.telegrambot.handler.inline.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anicmv.telegrambot.annotation.BotInline;
import com.github.anicmv.telegrambot.handler.inline.provider.InlineQueryResultProvider;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.utils.HttpUtil;
import com.github.anicmv.telegrambot.model.BotContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

@BotInline(BotConstant.INLINE_ID_DB)
@Component
@Log4j2
public class DoubanDbInlineQueryResultProvider implements InlineQueryResultProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String QUERY_PREFIX = "db";
    private static final int DB_PAGE_SIZE = 20;

    @Override
    public boolean supports(BotContext context) {
        String query = context == null ? null : context.text();
        if (query == null || query.isBlank()) {
            return true;
        }
        return query.trim().toLowerCase().startsWith(QUERY_PREFIX);
    }

    @Override
    public InlineQueryResult createResult(BotContext context) {
        String query = context == null || context.text() == null ? "" : context.text().trim();
        String keyword = extractKeyword(query);
        if (keyword.isBlank()) {
            return guideResult();
        }
        return searchResult(keyword);
    }

    @Override
    public List<InlineQueryResult> createResults(BotContext context) {
        String query = context == null || context.text() == null ? "" : context.text().trim();
        String keyword = extractKeyword(query);
        if (keyword.isBlank()) {
            return List.of(guideResult());
        }
        String url = buildSearchUrl(keyword);
        String response = fetchResponseText(url);
        if (response == null || response.isBlank()) {
            return List.of(searchResult(keyword));
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(normalizeJsonPayload(response));
            JsonNode items = root.path("items");
            List<InlineQueryResult> results = new ArrayList<>();
            for (JsonNode item : items) {
                String layout = item.path("layout").asText();
                if ("subject".equals(layout)) {
                    JsonNode target = item.path("target");
                    String type = decodeText(item.path("type_name").asText(""));
                    String title = decodeText(target.path("title").asText("未知标题"));
                    double rating = target.path("rating").path("value").asDouble(0.0);
                    String targetId = target.path("id").asText("");
                    addShortResult(results, type, title, rating, targetId);
                } else if ("doulist_cards".equals(layout)) {
                    JsonNode doulists = item.path("target").path("doulists");
                    if (doulists.isArray()) {
                        for (JsonNode doulist : doulists) {
                            String type = decodeText(doulist.path("image_label").asText("片单"));
                            String title = decodeText(doulist.path("title").asText("未知标题"));
                            String targetId = doulist.path("id").asText("");
                            addShortResult(results, type, title, 0.0, targetId);
                            if (results.size() >= DB_PAGE_SIZE) {
                                break;
                            }
                        }
                    }
                }
                if (results.size() >= DB_PAGE_SIZE) {
                    break;
                }
            }
            if (!results.isEmpty()) {
                return results;
            }
        } catch (Exception ex) {
            log.warn("Douban db createResults parse failed for keyword={}", keyword, ex);
        }
        return List.of(searchResult(keyword));
    }

    private void addShortResult(List<InlineQueryResult> results, String type, String title, double rating, String targetId) {
        String display = formatShortLine(type, title, rating);
        results.add(InlineQueryResultArticle.builder()
                .id(sortId() + "_" + (targetId == null || targetId.isBlank() ? results.size() + 1 : targetId))
                .title(display)
                .description(display)
                .inputMessageContent(InputTextMessageContent.builder().messageText(display).build())
                .build());
    }

    private InlineQueryResult guideResult() {
        return InlineQueryResultArticle.builder()
                .id(sortId())
                .title("豆瓣搜索")
                .description("点击发送后，可用按钮继续输入：db 关键词")
                .inputMessageContent(InputTextMessageContent.builder()
                        .messageText("豆瓣搜索入口\n点击下方按钮后输入：db 关键词")
                        .build())
                .replyMarkup(inputButton())
                .build();
    }

    private InlineQueryResult searchResult(String keyword) {
        String url = buildSearchUrl(keyword);
        String response = fetchResponseText(url);
        if (response == null || response.isBlank()) {
            log.warn("Douban db search empty response for keyword={}, url={}", keyword, url);
        }
        String message = buildResultText(response, keyword);
        return InlineQueryResultArticle.builder()
                .id(sortId())
                .title("豆瓣搜索: " + keyword)
                .description("点击发送结果，继续搜索可点按钮")
                .inputMessageContent(InputTextMessageContent.builder().messageText(message).build())
                .replyMarkup(inputButton())
                .build();
    }

    private String formatShortLine(String type, String title, double rating) {
        String safeType = (type == null || type.isBlank()) ? "未知" : type;
        String safeTitle = (title == null || title.isBlank()) ? "未知标题" : title;
        String rate = rating > 0 ? String.valueOf(rating) : "-";
        return safeType + " | " + safeTitle + " | " + rate;
    }

    private String buildSearchUrl(String keyword) {
        return buildSearchUrl(keyword, 0, 20);
    }

    private String buildSearchUrl(String keyword, int start, int count) {
        Map<String, String> params = new HashMap<>();
        params.put("q", keyword);
        params.put("start", String.valueOf(Math.max(start, 0)));
        params.put("count", String.valueOf(Math.max(count, 1)));
        params.put("apiKey", BotConstant.DB_API_KEY);
        return BotConstant.API_DB_SEARCH + "?" + HttpUtil.buildQueryString(params);
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Host", "frodo.douban.com");
        headers.put("Connection", "keep-alive");
        headers.put("Authorization", BotConstant.DB_AUTHORIZATION);
        headers.put(BotConstant.HEADER_USER_AGENT,
                "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 "
                        + "(KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.69(0x18004533) NetType/4G Language/zh_CN");
        headers.put(BotConstant.HEADER_REFERER, "https://servicewechat.com/wx2f9b06c1de1ccfca/99/page-frame.html");
        headers.put("Accept-Encoding", "gzip");
        headers.put("content-type", "application/json");
        return headers;
    }

    private String fetchResponseText(String url) {
        byte[] bytes = HttpUtil.getBytes(url, buildHeaders());
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        byte[] plain = tryDecompress(bytes);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private byte[] tryDecompress(byte[] bytes) {
        if (isGzip(bytes)) {
            try {
                return readAll(new GZIPInputStream(new ByteArrayInputStream(bytes)));
            } catch (IOException ignore) {
                return bytes;
            }
        }
        if (isZlib(bytes)) {
            try {
                return readAll(new InflaterInputStream(new ByteArrayInputStream(bytes)));
            } catch (IOException ignore) {
                return bytes;
            }
        }
        return bytes;
    }

    private boolean isGzip(byte[] bytes) {
        return bytes.length > 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b;
    }

    private boolean isZlib(byte[] bytes) {
        if (bytes.length < 2) {
            return false;
        }
        int b0 = bytes[0] & 0xff;
        int b1 = bytes[1] & 0xff;
        return b0 == 0x78 && (b1 == 0x01 || b1 == 0x9c || b1 == 0xda);
    }

    private byte[] readAll(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[4096];
        int read;
        int offset = 0;
        byte[] out = new byte[0];
        while ((read = inputStream.read(buffer)) > 0) {
            byte[] next = Arrays.copyOf(out, offset + read);
            System.arraycopy(buffer, 0, next, offset, read);
            out = next;
            offset += read;
        }
        return out;
    }

    private String buildResultText(String response, String keyword) {
        if (response == null || response.isBlank()) {
            return "豆瓣搜索失败：未获取到响应。\n关键词：" + keyword;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(normalizeJsonPayload(response));
            JsonNode items = root.path("items");
            for (JsonNode item : items) {
                if (!"subject".equals(item.path("layout").asText())) {
                    continue;
                }
                JsonNode target = item.path("target");
                String title = decodeText(target.path("title").asText("未知标题"));
                String type = decodeText(item.path("type_name").asText(""));
                String year = decodeText(target.path("year").asText(""));
                String subtitle = decodeText(target.path("card_subtitle").asText(""));
                String uri = decodeText(target.path("uri").asText(""));
                double rating = target.path("rating").path("value").asDouble(0.0);
                StringBuilder sb = new StringBuilder();
                sb.append("豆瓣搜索结果\n");
                sb.append("关键词：").append(keyword).append("\n");
                sb.append("标题：").append(title).append("\n");
                if (!type.isBlank()) {
                    sb.append("类型：").append(type).append("\n");
                }
                if (!year.isBlank()) {
                    sb.append("年份：").append(year).append("\n");
                }
                if (rating > 0) {
                    sb.append("评分：").append(rating).append("\n");
                }
                if (!subtitle.isBlank()) {
                    sb.append("简介：").append(subtitle).append("\n");
                }
                if (!uri.isBlank()) {
                    sb.append("链接：").append(uri);
                }
                return sb.toString();
            }
            return "豆瓣搜索结果为空。\n关键词：" + keyword;
        } catch (Exception ex) {
            log.warn("Douban db search parse failed for keyword={}, raw={}", keyword, preview(response), ex);
            return "豆瓣搜索解析失败。\n关键词：" + keyword + "\n原始响应预览：" + preview(response);
        }
    }

    private String normalizeJsonPayload(String response) {
        String trimmed = response == null ? "" : response.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String preview(String response) {
        if (response == null) {
            return "";
        }
        String compact = response.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.length() <= 120) {
            return compact;
        }
        return compact.substring(0, 120) + "...";
    }

    private String decodeText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            return value;
        }
    }

    private String extractKeyword(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String normalized = query.trim();
        if (!normalized.toLowerCase().startsWith(QUERY_PREFIX)) {
            return "";
        }
        String keyword = normalized.substring(QUERY_PREFIX.length()).trim();
        return keyword;
    }

    private InlineKeyboardMarkup inputButton() {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text("🔎 输入 db 关键词")
                .switchInlineQueryCurrentChat("db ")
                .build();
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(button)));
    }
}
