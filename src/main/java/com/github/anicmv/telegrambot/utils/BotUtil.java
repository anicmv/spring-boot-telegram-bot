package com.github.anicmv.telegrambot.utils;

import com.github.anicmv.telegrambot.model.BotContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

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
     * 提取命令参数：去掉首个空白前的命令本身，无参数返回空串。
     */
    public static String commandArgument(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        int firstBlank = trimmed.indexOf(' ');
        return firstBlank < 0 ? "" : trimmed.substring(firstBlank + 1).trim();
    }

    /**
     * 消息正文：text 优先，其次 caption；均为空返回空串。
     */
    public static String messageTextOrCaption(Message message) {
        if (message == null) {
            return "";
        }
        if (message.getText() != null && !message.getText().isBlank()) {
            return message.getText().trim();
        }
        if (message.getCaption() != null && !message.getCaption().isBlank()) {
            return message.getCaption().trim();
        }
        return "";
    }

    /**
     * 命令消息所回复的那条消息的正文；无回复返回空串。
     */
    public static String repliedMessageText(Message message) {
        if (message == null || message.getReplyToMessage() == null) {
            return "";
        }
        return messageTextOrCaption(message.getReplyToMessage());
    }

    /**
     * 命令参数优先；参数为空时取命令所回复消息的正文。
     */
    public static String commandArgumentOrReplyText(BotContext context) {
        if (context == null) {
            return "";
        }
        String argument = commandArgument(context.text());
        return argument.isBlank() ? repliedMessageText(context.message()) : argument;
    }

    /**
     * 尽力删除文件，失败不抛异常。
     */
    public static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }

    /**
     * 删除文件，并在其父目录名命中给定前缀（临时目录）时一并删除；失败不抛异常。
     */
    public static void deleteQuietly(Path file, String... tempParentPrefixes) {
        deleteQuietly(file);
        if (file == null) {
            return;
        }
        Path parent = file.getParent();
        if (parent == null || parent.getFileName() == null) {
            return;
        }
        String parentName = parent.getFileName().toString();
        for (String prefix : tempParentPrefixes) {
            if (parentName.startsWith(prefix)) {
                try {
                    Files.deleteIfExists(parent);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
                return;
            }
        }
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
