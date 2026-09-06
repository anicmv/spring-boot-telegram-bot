package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.Replier;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.service.AnimeFaceService;
import com.github.anicmv.telegrambot.service.AnimeFaceService.SearchResponse;
import com.github.anicmv.telegrambot.utils.BotUtil;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;

/**
 * /aniface：回复一条贴纸，识别其中的动漫/Gal 人物。
 */
@Log4j2
@Component
@BotCommand(value = BotConstant.CMD_ANIFACE, description = "动漫/Gal 人物识别：回复一条贴纸发送 /aniface")
public class AnimeFaceCommandHandler implements BotCommandHandler {

    private final Messenger messenger;
    private final AnimeFaceService animeFaceService;
    private final TaskExecutor botBackgroundExecutor;

    public AnimeFaceCommandHandler(Messenger messenger, AnimeFaceService animeFaceService,
                                   @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor) {
        this.messenger = messenger;
        this.animeFaceService = animeFaceService;
        this.botBackgroundExecutor = botBackgroundExecutor;
    }

    @Override
    public void execute(BotContext context) {
        Message command = context == null ? null : context.message();
        Message replied = command == null ? null : command.getReplyToMessage();
        Sticker sticker = replied != null && replied.hasSticker() ? replied.getSticker() : null;
        if (sticker == null || sticker.getFileId() == null || sticker.getFileId().isBlank()) {
            Replier.of(context, messenger).text("请回复一条贴纸消息后发送 /aniface");
            return;
        }
        Replier replier = Replier.of(context, messenger);
        Integer progressId = replier.htmlAndReturnId("<b>🔍 正在识别动漫/Gal 人物...</b>");
        try {
            botBackgroundExecutor.execute(() -> searchAndReply(context, sticker.getFileId(), progressId));
        } catch (RejectedExecutionException e) {
            log.warn("aniface 任务被拒绝: chatId={}", context == null ? null : context.chatId());
            replyOrEdit(replier, progressId, "<b>❌ 当前识图任务较多，请稍后重试</b>");
        }
    }

    private void searchAndReply(BotContext context, String fileId, Integer progressId) {
        Replier replier = Replier.of(context, messenger);
        try {
            byte[] imageBytes = messenger.downloadFileBytes(fileId);
            if (imageBytes == null || imageBytes.length == 0) {
                replyOrEdit(replier, progressId, "<b>❌ 获取贴纸文件失败，请稍后重试</b>");
                return;
            }
            SearchResponse response = animeFaceService.search(imageBytes);
            if (!response.success() || response.persons().isEmpty()) {
                replyOrEdit(replier, progressId,
                        "<b>❌ " + BotUtil.escapeHtml(response.message()) + "</b>");
                return;
            }
            replyOrEdit(replier, progressId, animeFaceService.formatHtml(response));
        } catch (Exception e) {
            log.warn("aniface 识别失败: chatId={}, fileId={}",
                    context == null ? null : context.chatId(), fileId, e);
            replyOrEdit(replier, progressId, "<b>❌ 识别失败，请稍后重试</b>");
        }
    }

    private void replyOrEdit(Replier replier, Integer messageId, String html) {
        if (messageId == null) {
            replier.html(html);
        } else {
            replier.editHtml(messageId, html);
        }
    }
}
