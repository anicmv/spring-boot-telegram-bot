package com.github.anicmv.telegrambot.handler.callback.impl;

import com.github.anicmv.telegrambot.annotation.BotCallback;
import com.github.anicmv.telegrambot.handler.callback.CallbackActionHandler;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.constant.XpCategory;
import com.github.anicmv.telegrambot.utils.BotUtil;
import com.github.anicmv.telegrambot.utils.HttpUtil;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import com.github.anicmv.telegrambot.config.BotProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description XP 系列回调处理器。
 */
@BotCallback(BotConstant.CALLBACK_ACTION_XP_PREFIX + "*")
@Component
@Log4j2
public class XpCallbackActionHandler implements CallbackActionHandler {

    private final Messenger messenger;
    private final BotProperties botProperties;
    /**
     * XP 图片数量有限且固定，缓存 类别 -> fileId，避免每次点击都向频道上传并刷屏。
     */
    private final Map<String, String> fileIdCache = new ConcurrentHashMap<>();

    public XpCallbackActionHandler(Messenger messenger, BotProperties botProperties) {
        this.messenger = messenger;
        this.botProperties = botProperties;
    }

    @Override
    public void execute(BotContext context, String payload) {
        CallbackQuery callbackQuery = context.callbackQuery();
        String rawAction = callbackQuery.getData();
        XpCategory xpEnum = XpCategory.fromCallback(rawAction);
        String imageUrl = resolveImageUrl(xpEnum);
        if (imageUrl == null || imageUrl.isBlank()) {
            messenger.answerCallback(callbackQuery.getId(), "获取图片失败");
            return;
        }
        String inlineMessageId = callbackQuery.getInlineMessageId();
        boolean inline = inlineMessageId != null && !inlineMessageId.isBlank();
        String fileId = fileIdCache.get(xpEnum.name());
        if (fileId == null) {
            fileId = inline
                    ? uploadInlineFileId(imageUrl)
                    : messenger.uploadPhotoViaChannel(botProperties.getChannelId(), imageUrl);
            if (fileId != null && !fileId.isBlank()) {
                fileIdCache.put(xpEnum.name(), fileId);
            }
        }
        User user = callbackQuery.getFrom();
        String clickableUsername = BotUtil.mentionMarkdownV2(user);


        // 其余文本同样需要转义（如果有特殊字符）
        String extraText = " 的xp是: " + xpEnum.getDescription();
        String escapedExtraText = BotUtil.escapeMarkdownV2(extraText);

        String caption = clickableUsername + escapedExtraText;
        if (inline) {
            if (fileId == null || fileId.isBlank()) {
                log.warn("Skip inline photo edit because upload did not return fileId: inlineMessageId={}, action={}",
                        inlineMessageId, rawAction);
                messenger.answerCallback(callbackQuery.getId(), "图片资源暂时不可用");
                return;
            }
            messenger.editInlineMessagePhoto(inlineMessageId, fileId, caption, "MarkdownV2");
        } else {
            String media = fileId != null && !fileId.isBlank() ? fileId : imageUrl;
            messenger.sendPhotoByUrl(context.chatId(), media, caption, "MarkdownV2");
        }
        messenger.answerCallback(callbackQuery.getId(), "已切换图片");
    }

    private String resolveImageUrl(XpCategory xpEnum) {
        String api = xpEnum.getApi();
        if (api.endsWith(".php")) {
            return HttpUtil.redirectUrl(api, Map.of(BotConstant.HEADER_USER_AGENT, BotConstant.USER_AGENT));
        }
        List<String> directApis = List.of(api);
        return directApis.getFirst();
    }

    private String uploadInlineFileId(String imageUrl) {
        if (botProperties.getChannelId() == null) {
            log.warn("Skip inline XP upload because channelId is not configured");
            return null;
        }
        Path tempFile = null;
        try {
            byte[] bytes = HttpUtil.getBytes(imageUrl, Map.of(BotConstant.HEADER_USER_AGENT, BotConstant.USER_AGENT));
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            tempFile = Files.createTempFile("xp-inline-", ".img");
            Files.write(tempFile, bytes);
            return messenger.uploadPhotoViaChannel(botProperties.getChannelId(), tempFile.toString());
        } catch (Exception exception) {
            log.warn("Failed to prepare XP inline upload from {}", imageUrl, exception);
            return null;
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception exception) {
                    log.debug("Failed to delete temp XP image {}", tempFile, exception);
                }
            }
        }
    }
}
