package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.Replier;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.service.StickerPackService;
import com.github.anicmv.telegrambot.utils.BotUtil;
import java.nio.file.Path;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;

/**
 * @description /sticker：回复一条贴纸消息，只下载并转换当前这一张贴纸。
 */
@Log4j2
@Component
@BotCommand(value = BotConstant.CMD_STICKER, description = "回复一条贴纸消息，下载并转换单张贴纸")
public class StickerCommandHandler implements BotCommandHandler {

    private final Messenger messenger;
    private final StickerPackService stickerPackService;
    private final TaskExecutor botBackgroundExecutor;

    public StickerCommandHandler(Messenger messenger,
                                  StickerPackService stickerPackService,
                                  @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor) {
        this.messenger = messenger;
        this.stickerPackService = stickerPackService;
        this.botBackgroundExecutor = botBackgroundExecutor;
    }

    @Override
    public void execute(BotContext context) {
        Replier replier = Replier.of(context, messenger);
        Message replied = context == null || context.message() == null
                ? null : context.message().getReplyToMessage();
        Sticker sticker = replied != null && replied.hasSticker() ? replied.getSticker() : null;
        if (sticker == null) {
            replier.text("请回复一条贴纸消息后发送 /sticker");
            return;
        }

        Integer progressMessageId = replier.htmlAndReturnId("<b>▎下 载 中...</b>");
        try {
            botBackgroundExecutor.execute(() -> downloadAndSend(context, sticker, progressMessageId));
        } catch (RejectedExecutionException e) {
            log.warn("sticker 任务被拒绝: chatId={}", context == null ? null : context.chatId());
            replier.editHtml(progressMessageId, "<b>▎下 载</b>\n系统繁忙，请稍后重试。");
        }
    }

    private void downloadAndSend(BotContext context, Sticker sticker, Integer progressMessageId) {
        Replier replier = Replier.of(context, messenger);
        StickerPackService.PreparedSticker prepared = null;
        try {
            prepared = stickerPackService.prepareSingle(sticker);
            replier.editHtml(progressMessageId, "<b>▎上 传 中...</b>");
            String caption = "🧩 <b>单张贴纸</b> <code>" +
                    BotUtil.escapeHtml(prepared.extension()) + "</code>";
            boolean sent = replier.documentByPath(prepared.file().toString(), caption);
            if (sent) {
                replier.deleteSilently(progressMessageId);
            } else {
                replier.editHtml(progressMessageId, "<b>▎失 败</b>\n文件发送失败，请稍后重试。");
            }
        } catch (IllegalStateException e) {
            replier.editHtml(progressMessageId, "<b>▎失 败</b>\n" + BotUtil.escapeHtml(e.getMessage()));
        } catch (Exception e) {
            log.warn("单张贴纸处理失败: chatId={}, fileId={}",
                    context == null ? null : context.chatId(), sticker == null ? null : sticker.getFileId(), e);
            replier.editHtml(progressMessageId, "<b>▎失 败</b>\n贴纸处理失败，请稍后重试。");
        } finally {
            if (prepared != null) {
                BotUtil.deleteDirectoryQuietly(prepared.file().getParent());
            }
        }
    }
}
