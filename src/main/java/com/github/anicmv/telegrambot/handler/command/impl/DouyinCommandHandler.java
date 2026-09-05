package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.service.DouyinVideoService;
import com.github.anicmv.telegrambot.service.DouyinVideoService.DownloadedVideo;
import com.github.anicmv.telegrambot.service.DouyinVideoService.ResolvedVideo;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.utils.BotUtil;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.Replier;
import com.github.anicmv.telegrambot.model.BotContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/5/3 15:30
 * @description /douyin 命令处理器，解析并发送抖音视频。
 */
@Log4j2
@BotCommand(value = BotConstant.CMD_DOUYIN, description = "下载抖音视频，格式：/douyin 抖音链接")
@Component
public class DouyinCommandHandler implements BotCommandHandler {

    private final Messenger messenger;
    private final DouyinVideoService douyinVideoService;
    private final TaskExecutor botBackgroundExecutor;
    private final boolean uploadVideoEnabled;

    public DouyinCommandHandler(Messenger messenger,
                                DouyinVideoService douyinVideoService,
                                @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor,
                                @Value("${bot.douyin.upload-video-enabled:true}") boolean uploadVideoEnabled) {
        this.messenger = messenger;
        this.douyinVideoService = douyinVideoService;
        this.botBackgroundExecutor = botBackgroundExecutor;
        this.uploadVideoEnabled = uploadVideoEnabled;
    }

    @Override
    public void execute(BotContext context) {
        String text = BotUtil.commandArgumentOrReplyText(context);
        if (text.isBlank()) {
            Replier.of(context, messenger).text("请发送：/douyin 抖音链接，或回复含抖音链接的消息后发送 /douyin");
            return;
        }

        botBackgroundExecutor.execute(() -> downloadAndSend(context, text));
    }

    private void downloadAndSend(BotContext context, String text) {
        Replier replier = Replier.of(context, messenger);
        DownloadedVideo video = null;
        Integer progressMessageId = null;
        try {
            progressMessageId = replier.htmlAndReturnId("<b>▎解 析 中...</b>");
            log.info("开始处理抖音请求。chatId={}, uploadVideoEnabled={}", context.chatId(), uploadVideoEnabled);
            if (!uploadVideoEnabled) {
                ResolvedVideo resolved = douyinVideoService.resolve(text);
                replier.editHtml(progressMessageId, buildCaption(resolved));
                log.info("抖音上传已禁用，仅发送富文本。chatId={}, videoId={}", context.chatId(), resolved.id());
                return;
            }
            video = douyinVideoService.download(text);
            replier.editHtml(progressMessageId, "<b>▎上 传 中...</b>");
            boolean sent = replier.videoByPath(video.path().toString(), buildCaption(video));
            if (sent) {
                replier.deleteSilently(progressMessageId);
                log.info("抖音视频上传发送成功。chatId={}, videoId={}, localPath={}",
                        context.chatId(), video.id(), video.path());
            } else {
                log.warn("抖音上传失败，改为仅发送富文本。chatId={}, videoId={}", context.chatId(), video.id());
                if (progressMessageId != null) {
                    replier.editHtml(progressMessageId, buildCaption(video));
                } else {
                    replier.html(buildCaption(video));
                }
            }
        } catch (Exception e) {
            log.warn("抖音处理失败。chatId={}, text={}", context.chatId(), text, e);
            if (progressMessageId != null) {
                replier.editHtml(progressMessageId, "<b>▎失 败</b>");
            } else {
                replier.html("<b>▎失 败</b>");
            }
        } finally {
            if (video != null) {
                BotUtil.deleteQuietly(video.path(), "douyin-");
            }
        }
    }

    private static String buildCaption(DownloadedVideo video) {
        return buildCaption(
                video.id(),
                video.desc(),
                video.author(),
                video.sourceUrl(),
                video.realVideoUrl()
        );
    }

    private static String buildCaption(ResolvedVideo video) {
        return buildCaption(
                video.id(),
                video.desc(),
                video.author(),
                video.sourceUrl(),
                video.realVideoUrl()
        );
    }

    private static String buildCaption(String id, String desc, String author, String sourceUrl, String realVideoUrl) {
        String title = desc == null || desc.isBlank() ? ("抖音视频 " + id) : desc;
        StringBuilder caption = new StringBuilder();
        caption.append("<b>").append(BotUtil.escapeHtml(title)).append("</b>");
        if (author != null && !author.isBlank()) {
            caption.append("\n\n<blockquote>作者：")
                    .append(BotUtil.escapeHtml(author))
                    .append("</blockquote>");
        }
        boolean hasSource = sourceUrl != null && !sourceUrl.isBlank();
        boolean hasResolved = realVideoUrl != null && !realVideoUrl.isBlank();
        if (hasSource || hasResolved) {
            caption.append("\n\n▎");
            if (hasSource) {
                caption.append("<a href=\"").append(BotUtil.escapeHtml(sourceUrl)).append("\">视频链接</a>");
            }
            if (hasSource && hasResolved) {
                caption.append("  |  ");
            }
            if (hasResolved) {
                caption.append("<a href=\"").append(BotUtil.escapeHtml(realVideoUrl)).append("\">立即观看</a>");
            }
        }
        String value = caption.toString();
        return value.length() > 1000 ? value.substring(0, 997) + "..." : value;
    }
}
