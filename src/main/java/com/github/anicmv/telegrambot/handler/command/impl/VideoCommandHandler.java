package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.service.VideoDownloadService;
import com.github.anicmv.telegrambot.service.VideoDownloadService.DownloadedFile;
import com.github.anicmv.telegrambot.service.VideoDownloadService.Platform;
import com.github.anicmv.telegrambot.utils.BotUtil;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.Replier;
import com.github.anicmv.telegrambot.model.BotContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /video 命令处理器，自动识别平台（YouTube/Instagram/小红书）并下载发送。
 */
@Log4j2
@BotCommand(value = BotConstant.CMD_VIDEO, description = "下载 YouTube/Instagram/小红书 视频")
@Component
public class VideoCommandHandler implements BotCommandHandler {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[^\\s]+", Pattern.CASE_INSENSITIVE);

    private final Messenger messenger;
    private final VideoDownloadService videoDownloadService;
    private final TaskExecutor botBackgroundExecutor;

    public VideoCommandHandler(Messenger messenger,
                              VideoDownloadService videoDownloadService,
                              @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor) {
        this.messenger = messenger;
        this.videoDownloadService = videoDownloadService;
        this.botBackgroundExecutor = botBackgroundExecutor;
    }

    @Override
    public void execute(BotContext context) {
        String text = resolveInputText(context);
        if (text.isBlank()) {
            Replier.of(context, messenger).text("请发送：/video YouTube/Instagram/小红书链接");
            return;
        }
        botBackgroundExecutor.execute(() -> downloadAndSend(context, text));
    }

    private void downloadAndSend(BotContext context, String text) {
        Replier replier = Replier.of(context, messenger);
        Integer progressMsgId = null;
        DownloadedFile downloadedFile = null;
        try {
            log.info("downloadAndSend 收到 text=[{}]", text);
            String url = extractFirstUrl(text);
            log.info("extractFirstUrl 提取结果 url=[{}]", url);
            if (url.isBlank()) {
                replier.text("未找到有效的视频链接，请发送 YouTube、Instagram 或小红书链接。");
                return;
            }

            Platform platform = videoDownloadService.detectPlatform(url);
            if (platform == Platform.UNKNOWN) {
                replier.text("无法识别链接平台，仅支持 YouTube、Instagram、小红书。");
                return;
            }

            progressMsgId = replier.htmlAndReturnId("<b>▎解 析 中...</b>");
            log.info("开始下载视频。platform={}, url={}, chatId={}", platform.displayName, url, context.chatId());

            downloadedFile = videoDownloadService.download(url);
            replier.editHtml(progressMsgId, "<b>▎上 传 中...</b>");

            String caption = buildCaption(downloadedFile);
            boolean sent = replier.videoByPath(downloadedFile.path().toString(), caption);

            if (sent) {
                replier.deleteSilently(progressMsgId);
                log.info("视频上传发送成功。platform={}, chatId={}", platform.displayName, context.chatId());
            } else {
                replier.editHtml(progressMsgId, caption);
            }
        } catch (Exception e) {
            log.warn("视频处理失败。chatId={}, text={}", context.chatId(), text, e);
            String errMsg = buildErrorMessage(e);
            if (progressMsgId != null) {
                replier.editHtml(progressMsgId, "<b>▎失 败</b>\n" + errMsg);
            } else {
                replier.html("<b>▎失 败</b>\n" + errMsg);
            }
        } finally {
            if (downloadedFile != null) {
                BotUtil.deleteQuietly(downloadedFile.path(), "youtube", "instagram", "xiaohongshu");
            }
        }
    }

    private String extractFirstUrl(String text) {
        if (text == null || text.isBlank()) return "";
        Matcher matcher = URL_PATTERN.matcher(text);
        String url = matcher.find() ? matcher.group(0) : text.strip();
        url = url.replaceAll("[，。！？、)）]+$", "");
        if (!url.startsWith("http")) return "";
        return url;
    }

    private String resolveInputText(BotContext context) {
        if (context == null || context.message() == null) return "";

        // Try to extract full URL from message entities first (Telegram truncates & in text)
        String fromEntity = extractUrlFromMessageEntities(context.message());
        if (!fromEntity.isBlank()) return fromEntity;

        // Fallback to text
        String direct = extractFirstUrl(context.text());
        if (!direct.isBlank()) return direct;

        // Check reply message
        Message replied = context.message().getReplyToMessage();
        if (replied != null) {
            String entityUrl = extractUrlFromMessageEntities(replied);
            if (!entityUrl.isBlank()) return entityUrl;
            return BotUtil.messageTextOrCaption(replied);
        }
        return "";
    }

    private String extractUrlFromMessageEntities(Message message) {
        if (message == null || message.getEntities() == null) return "";
        for (var entity : message.getEntities()) {
            if ("url".equalsIgnoreCase(entity.getType()) && entity.getText() != null) {
                String url = entity.getText().trim();
                if (url.startsWith("http")) return url;
            }
        }
        return "";
    }

    private String buildCaption(DownloadedFile file) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>📹 ").append(BotUtil.escapeHtml(file.platform())).append(" 视频</b>");
        if (file.filename() != null && !file.filename().isBlank()) {
            String name = file.filename().replaceAll("\\.[^.]+$", "");
            if (!name.isBlank()) {
                sb.append("\n\n<blockquote>").append(BotUtil.escapeHtml(name)).append("</blockquote>");
            }
        }
        if (file.originalUrl() != null && !file.originalUrl().isBlank()) {
            sb.append("\n\n▎<a href=\"").append(BotUtil.escapeHtml(file.originalUrl()))
                    .append("\">原始链接</a>");
        }
        return sb.toString();
    }

    private String buildErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) msg = "未知错误";
        if (msg.contains("无法识别链接平台")) return "⚠️ " + msg;
        if (msg.contains("yt-dlp 未找到")) return "⚠️ 下载工具异常，请稍后重试。";
        if (msg.contains("文件下载失败")) return "⚠️ " + msg;
        return "⚠️ " + msg;
    }
}
