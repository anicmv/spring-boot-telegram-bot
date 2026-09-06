package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.messenger.Replier;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.service.AnimeFaceService;
import com.github.anicmv.telegrambot.service.AnimeFaceService.SearchResponse;
import com.github.anicmv.telegrambot.service.BangumiWorkTranslationService;
import com.github.anicmv.telegrambot.service.StickerImageService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * /aniface：回复一条贴纸，识别其中的动漫/Gal 人物。
 */
@Log4j2
@Component
@BotCommand(value = BotConstant.CMD_ANIFACE, description = "动漫/Gal 人物识别：回复一条贴纸发送 /aniface")
public class AnimeFaceCommandHandler implements BotCommandHandler {

    private final Messenger messenger;
    private final AnimeFaceService animeFaceService;
    private final StickerImageService stickerImageService;
    private final BangumiWorkTranslationService bangumiWorkTranslationService;
    private final TaskExecutor botBackgroundExecutor;
    private final TaskScheduler botScheduler;

    public AnimeFaceCommandHandler(Messenger messenger, AnimeFaceService animeFaceService,
                                   StickerImageService stickerImageService,
                                   BangumiWorkTranslationService bangumiWorkTranslationService,
                                   @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor,
                                   @Qualifier("botScheduler") TaskScheduler botScheduler) {
        this.messenger = messenger;
        this.animeFaceService = animeFaceService;
        this.stickerImageService = stickerImageService;
        this.bangumiWorkTranslationService = bangumiWorkTranslationService;
        this.botBackgroundExecutor = botBackgroundExecutor;
        this.botScheduler = botScheduler;
    }

    @Override
    public void execute(BotContext context) {
        Message command = context == null ? null : context.message();
        Message replied = command == null ? null : command.getReplyToMessage();
        Sticker sticker = replied != null && replied.hasSticker() ? replied.getSticker() : null;
        List<org.telegram.telegrambots.meta.api.objects.photo.PhotoSize> photos =
                replied != null && replied.hasPhoto() ? replied.getPhoto() : List.of();
        String photoFileId = photos == null ? null : photos.stream()
                .filter(photo -> photo != null && photo.getFileId() != null && !photo.getFileId().isBlank())
                .max(Comparator.comparingLong(this::photoArea))
                .map(org.telegram.telegrambots.meta.api.objects.photo.PhotoSize::getFileId)
                .orElse(null);
        boolean hasSticker = sticker != null && sticker.getFileId() != null && !sticker.getFileId().isBlank();
        boolean hasPhoto = photoFileId != null;
        if (!hasSticker && !hasPhoto) {
            Replier.of(context, messenger).text("请回复一张图片或贴纸后发送 /aniface");
            return;
        }
        String fileId = hasSticker ? sticker.getFileId() : photoFileId;
        Replier replier = Replier.of(context, messenger);
        Integer progressId = replier.htmlAndReturnId("<b>🔍 正在识别动漫/Gal 人物...</b>");
        try {
            botBackgroundExecutor.execute(() -> searchAndReply(context, sticker, fileId, progressId));
        } catch (RejectedExecutionException e) {
            log.warn("aniface 任务被拒绝: chatId={}", context == null ? null : context.chatId());
            replyOrEdit(replier, progressId, "<b>❌ 当前识图任务较多，请稍后重试</b>");
        }
    }

    private long photoArea(org.telegram.telegrambots.meta.api.objects.photo.PhotoSize photo) {
        if (photo == null || photo.getWidth() <= 0 || photo.getHeight() <= 0) {
            return 0L;
        }
        return (long) photo.getWidth() * photo.getHeight();
    }

    private void searchAndReply(BotContext context, Sticker sticker, String fileId, Integer progressId) {
        Replier replier = Replier.of(context, messenger);
        try {
            byte[] imageBytes = messenger.downloadFileBytes(fileId);
            if (imageBytes == null || imageBytes.length == 0) {
                temporaryError(replier, context, progressId);
                return;
            }
            byte[] normalizedImage = sticker == null
                    ? stickerImageService.normalizePhoto(imageBytes)
                    : stickerImageService.normalize(sticker, imageBytes);
            SearchResponse response = animeFaceService.search(normalizedImage);
            if (!response.success() || response.persons().isEmpty()) {
                temporaryError(replier, context, progressId);
                return;
            }
            replyOrEdit(replier, progressId, animeFaceService.formatHtml(response));
            log.info("aniface 识别结果已发送原文: chatId={}, progressId={}, traceId={}, works={}",
                    context == null ? null : context.chatId(), progressId, response.traceId(),
                    response.persons().stream().flatMap(person -> person.candidates().stream())
                            .map(AnimeFaceService.Candidate::work).toList());
            translateAsync(replier, response, progressId);
        } catch (Exception e) {
            log.warn("aniface 识别失败: chatId={}, fileId={}",
                    context == null ? null : context.chatId(), fileId, e);
            temporaryError(replier, context, progressId);
        }
    }

    private void translateAsync(Replier replier, SearchResponse response, Integer progressId) {
        try {
            bangumiWorkTranslationService.translateAsync(response).whenComplete((result, error) -> {
                if (error != null) {
                    log.warn("Bangumi 翻译任务失败: traceId={}", response.traceId(), error);
                    return;
                }
                if (result == null || (result.workTranslations().isEmpty()
                        && result.characterTranslations().isEmpty())) {
                    log.warn("Bangumi 没有匹配到中文名: traceId={}", response.traceId());
                    return;
                }
                log.info("Bangumi 翻译完成: traceId={}, works={}, characters={}",
                        response.traceId(), result.workTranslations(), result.characterTranslations());
                try {
                    replyOrEdit(replier, progressId, animeFaceService.formatHtml(
                            response, result.workTranslations(), result.characterTranslations()));
                } catch (RuntimeException e) {
                    log.warn("Bangumi 中文名更新失败", e);
                }
            });
        } catch (RuntimeException e) {
            log.warn("提交 Bangumi 翻译任务失败", e);
        }
    }
    private void temporaryError(Replier replier, BotContext context, Integer progressId) {
        String error = "<b>❌ 出了点意外...</b>";
        replyOrEdit(replier, progressId, error);
        if (progressId == null || context == null || context.chatId() == null) {
            return;
        }
        try {
            botScheduler.schedule(
                    () -> messenger.deleteMessageSilently(context.chatId(), progressId),
                    Instant.now().plusSeconds(3));
        } catch (RuntimeException e) {
            log.warn("调度识别错误消息删除失败: chatId={}, messageId={}",
                    context.chatId(), progressId, e);
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
