package com.github.anicmv.telegrambot.service;

import com.github.anicmv.telegrambot.utils.BotUtil;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多平台视频解析下载服务，支持：
 * - YouTube
 * - Instagram
 * - 小红书
 */
@Log4j2
@Service
public class VideoDownloadService {

    private static final Pattern YT_PATTERN = Pattern.compile(
            "(?:youtube\\.com|youtu\\.be)\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern IG_PATTERN = Pattern.compile(
            "(?:instagram\\.com|instagr\\.am)\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern XHS_PATTERN = Pattern.compile(
            "(?:xiaohongshu\\.com|xhslink\\.com)\\S+", Pattern.CASE_INSENSITIVE);

    private static final Duration YT_DLP_TIMEOUT = Duration.ofMinutes(10);
    private static final int YT_DLP_LOG_TAIL_LIMIT = 2000;

    public record DownloadedFile(Path path, String filename, String platform, String originalUrl) {
    }

    public record ResolveResult(String id, String title, String author, String platform, String downloadUrl) {
    }

    public enum Platform {
        YOUTUBE("YouTube", "yt-dlp"),
        INSTAGRAM("Instagram", "yt-dlp"),
        XIAOHONGSHU("小红书", "yt-dlp"),
        UNKNOWN("Unknown", null);

        public final String displayName;
        public final String tool;

        Platform(String displayName, String tool) {
            this.displayName = displayName;
            this.tool = tool;
        }
    }

    public Platform detectPlatform(String text) {
        if (YT_PATTERN.matcher(text).find()) return Platform.YOUTUBE;
        if (IG_PATTERN.matcher(text).find()) return Platform.INSTAGRAM;
        if (XHS_PATTERN.matcher(text).find()) return Platform.XIAOHONGSHU;
        return Platform.UNKNOWN;
    }

    public DownloadedFile download(String url) {
        Platform platform = detectPlatform(url);
        if (platform == Platform.UNKNOWN) {
            throw new IllegalArgumentException("无法识别链接平台，仅支持 YouTube、Instagram、小红书。");
        }
        return downloadWithYtDlp(url, platform);
    }

    private DownloadedFile downloadWithYtDlp(String url, Platform platform) {
        Path tempDir;
        Path logFile;
        try {
            tempDir = Files.createTempDirectory(platform.name().toLowerCase() + "-");
            logFile = Files.createTempFile("yt-dlp-", ".log");
        } catch (IOException e) {
            throw new IllegalStateException("创建临时下载目录失败。", e);
        }

        String[] cmd = buildYtDlpCommand(url, tempDir);
        log.info("执行下载命令：{}", String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectOutput(logFile.toFile());
        boolean success = false;
        Process process = null;
        try {
            process = pb.start();
            boolean finished = process.waitFor(YT_DLP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                log.warn("yt-dlp 下载超时（{}），强制终止。url={}", YT_DLP_TIMEOUT, url);
                throw new IllegalStateException("下载超时，已中止，请稍后重试。");
            }
            int exitCode = process.exitValue();
            log.info("yt-dlp 退出码：{}，平台：{}", exitCode, platform.displayName);

            if (exitCode != 0) {
                log.warn("yt-dlp 下载失败，输出：{}", readLogTail(logFile));
            }

            Path downloadedFile = findDownloadedFile(tempDir);
            if (downloadedFile == null) {
                throw new IllegalStateException("文件下载失败，yt-dlp 未找到输出文件。退出码：" + exitCode);
            }

            DownloadedFile result = new DownloadedFile(
                    downloadedFile, downloadedFile.getFileName().toString(), platform.displayName, url);
            success = true;
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("下载过程异常。", e);
        } catch (IOException e) {
            throw new IllegalStateException("下载过程异常。", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteQuietly(logFile);
            if (!success) {
                BotUtil.deleteDirectoryQuietly(tempDir);
            }
        }
    }

    private String readLogTail(Path logFile) {
        try {
            String content = Files.readString(logFile, StandardCharsets.UTF_8);
            if (content.length() <= YT_DLP_LOG_TAIL_LIMIT) {
                return content;
            }
            return content.substring(content.length() - YT_DLP_LOG_TAIL_LIMIT);
        } catch (IOException e) {
            return "<无法读取日志: " + e.getMessage() + ">";
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }

    private String[] buildYtDlpCommand(String url, Path outputDir) {
        return new String[]{
                "yt-dlp",
                "--no-playlist",
                "--no-warnings",
                "-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
                "--merge-output-format", "mp4",
                "-o", outputDir.resolve("%(title)s.%(ext)s").toString(),
                url
        };
    }

    private Path findDownloadedFile(Path dir) {
        try {
            return Files.list(dir)
                    .filter(p -> !Files.isDirectory(p))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("搜索下载文件失败。", e);
            return null;
        }
    }

    public Map<String, String> buildCaption(DownloadedFile file, String title) {
        Map<String, String> caption = new LinkedHashMap<>();
        caption.put("platform", file.platform());
        caption.put("filename", file.filename());
        caption.put("originalUrl", file.originalUrl());
        if (title != null && !title.isBlank()) {
            caption.put("title", title);
        }
        return caption;
    }
}
