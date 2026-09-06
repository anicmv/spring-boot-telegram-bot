package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.Replier;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.service.StickerPackService;
import com.github.anicmv.telegrambot.utils.BotUtil;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;

/**
 * @author anicmv
 * @date 2026/9/5
 * @description /pack：回复一条贴纸消息，拉取该贴纸包全部贴纸打 zip 回发给命令发送者。
 * 异步三段式：占位消息 → 后台打包 → 成功发文档删占位 / 失败占位改错误文案。
 */
@Log4j2
@Component
@BotCommand(value = BotConstant.CMD_PACK, description = "回复一条贴纸消息，打包其所在贴纸包为 ZIP")
public class PackCommandHandler implements BotCommandHandler {

    private static final int PROGRESS_EDIT_INTERVAL = 20;

    private final Messenger messenger;
    private final StickerPackService stickerPackService;
    private final TaskExecutor botBackgroundExecutor;

    public PackCommandHandler(Messenger messenger,
                              StickerPackService stickerPackService,
                              @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor) {
        this.messenger = messenger;
        this.stickerPackService = stickerPackService;
        this.botBackgroundExecutor = botBackgroundExecutor;
    }

    @Override
    public void execute(BotContext context) {
        Replier replier = Replier.of(context, messenger);
        Message replied = context.message() == null ? null : context.message().getReplyToMessage();
        Sticker sticker = replied != null && replied.hasSticker() ? replied.getSticker() : null;
        if (sticker == null) {
            replier.text("请回复一条贴纸消息后发送 /pack");
            return;
        }
        String setName = sticker.getSetName();
        if (setName == null || setName.isBlank()) {
            replier.text("该贴纸不属于任何贴纸包，无法打包");
            return;
        }
        Integer progressMessageId = replier.htmlAndReturnId("<b>▎打 包 中...</b>");
        try {
            botBackgroundExecutor.execute(() -> packAndSend(context, setName, progressMessageId));
        } catch (RejectedExecutionException e) {
            log.warn("pack 任务被拒绝: chatId={}", context.chatId());
            replier.editHtml(progressMessageId, "<b>▎打 包</b>\n系统繁忙，请稍后重试。");
        }
    }

    private void packAndSend(BotContext context, String setName, Integer progressMessageId) {
        Replier replier = Replier.of(context, messenger);
        StickerPackService.PackedStickerSet result = null;
        try {
            result = stickerPackService.pack(setName, processed -> {
                if (processed % PROGRESS_EDIT_INTERVAL == 0) {
                    replier.editHtml(progressMessageId, "<b>▎打 包 中 " + processed + "...</b>");
                }
            });
            boolean sent = replier.documentByPath(result.zipPath().toString(), buildCaption(result));
            if (sent) {
                replier.deleteSilently(progressMessageId);
            } else {
                replier.editHtml(progressMessageId, "<b>▎失 败</b>\n文件发送失败，请稍后重试。");
            }
        } catch (IllegalStateException e) {
            replier.editHtml(progressMessageId, "<b>▎失 败</b>\n" + BotUtil.escapeHtml(e.getMessage()));
        } catch (Exception e) {
            log.warn("贴纸包打包失败: chatId={}, setName={}", context.chatId(), setName, e);
            replier.editHtml(progressMessageId, "<b>▎失 败</b>\n打包失败，请稍后重试。");
        } finally {
            if (result != null) {
                BotUtil.deleteDirectoryQuietly(result.zipPath().getParent());
            }
        }
    }

    private String buildCaption(StickerPackService.PackedStickerSet result) {
        StringBuilder caption = new StringBuilder("📦 <b>")
                .append(BotUtil.escapeHtml(result.title()))
                .append("</b>\n▎已收录 ")
                .append(result.packedCount()).append('/').append(result.totalCount()).append(" 张");
        if (result.skippedCount() > 0) {
            caption.append("，").append(result.skippedCount()).append(" 张处理失败");
        }
        if (result.truncated()) {
            caption.append("\n⚠️ 达到体积上限，已截断");
        }
        return caption.toString();
    }
}
