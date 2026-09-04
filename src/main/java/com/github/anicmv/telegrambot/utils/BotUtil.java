package com.github.anicmv.telegrambot.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.telegram.telegrambots.meta.api.objects.User;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description Bot 通用工具类。
 */
public final class BotUtil {

    private static final char[] MARKDOWN_V2_SPECIALS = {
            '\\', '_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '-', '=', '|', '{', '}', '.', '!'
    };

    private BotUtil() {
    }

    public static String escapeMarkdownV2(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String escaped = text;
        for (char special : MARKDOWN_V2_SPECIALS) {
            escaped = escaped.replace(String.valueOf(special), "\\" + special);
        }
        return escaped;
    }

    public static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static String mentionMarkdownV2(Long userId, String fullName) {
        String safeName = escapeMarkdownV2(fullName);
        if (userId == null) {
            return safeName;
        }
        return "[" + safeName + "](tg://user?id=" + userId + ")";
    }

    public static String formatUserName(User user) {
        if (user == null) {
            return "unknown";
        }
        String firstName = safeTrim(user.getFirstName());
        String lastName = safeTrim(user.getLastName());
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }
        String username = safeTrim(user.getUserName());
        if (!username.isEmpty()) {
            return username;
        }
        Long userId = user.getId();
        return userId == null ? "unknown" : String.valueOf(userId);
    }

    public static String mentionMarkdownV2(User user) {
        if (user == null) {
            return "unknown";
        }
        return mentionMarkdownV2(user.getId(), formatUserName(user));
    }

    public static <T> T randomOne(List<T> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(items.size());
        return items.get(index);
    }

    /**
     * 尽力递归删除目录，失败不抛异常。
     */
    public static void deleteDirectoryQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // best effort cleanup
                        }
                    });
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }

    private static String safeTrim(String text) {
        return text == null ? "" : text.trim();
    }
}
