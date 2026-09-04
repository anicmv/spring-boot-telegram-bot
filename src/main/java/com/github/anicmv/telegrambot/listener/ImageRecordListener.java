package com.github.anicmv.telegrambot.listener;

import com.github.anicmv.telegrambot.entity.ChatImageEntity;
import com.github.anicmv.telegrambot.event.GroupMessageReceivedEvent;
import com.github.anicmv.telegrambot.listener.filter.GroupMessageFilter;
import com.github.anicmv.telegrambot.repository.ChatImageRepository;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 静态图片记录监听器：消息通过完整过滤链（群聊、bot 发言、开关白名单、静态图片）
 * 后，异步下载图片（静态贴纸 webp / 用户图片）并按 file_unique_id 去重落库，不阻塞 update 主链路。
 */
@Log4j2
@Component
public class ImageRecordListener {

    private final TelegramClient telegramClient;
    private final ChatImageRepository chatImageRepository;
    private final TaskExecutor botBackgroundExecutor;
    private final List<GroupMessageFilter> filters;

    public ImageRecordListener(TelegramClient telegramClient,
                               ChatImageRepository chatImageRepository,
                               @Qualifier("botBackgroundExecutor") TaskExecutor botBackgroundExecutor,
                               List<GroupMessageFilter> filters) {
        this.telegramClient = telegramClient;
        this.chatImageRepository = chatImageRepository;
        this.botBackgroundExecutor = botBackgroundExecutor;
        this.filters = filters;
    }

    @EventListener
    public void onGroupMessage(GroupMessageReceivedEvent event) {
        if (event == null || !passFilters(event)) {
            return;
        }
        try {
            botBackgroundExecutor.execute(() -> saveSafely(event));
        } catch (RejectedExecutionException e) {
            log.warn("图片记录任务被拒绝: chatId={}, messageId={}", event.chatId(), event.telegramMessageId());
        }
    }

    private boolean passFilters(GroupMessageReceivedEvent event) {
        for (GroupMessageFilter filter : filters) {
            if (!filter.accept(event)) {
                return false;
            }
        }
        return true;
    }

    private void saveSafely(GroupMessageReceivedEvent event) {
        try {
            org.telegram.telegrambots.meta.api.objects.File fileInfo =
                    telegramClient.execute(new GetFile(resolveFileId(event)));
            byte[] imageData;
            try (InputStream in = telegramClient.downloadFileAsStream(fileInfo)) {
                imageData = in.readAllBytes();
            }
            chatImageRepository.insert(toEntity(event, imageData));
        } catch (Exception e) {
            log.error("图片落库失败: chatId={}, messageId={}", event.chatId(), event.telegramMessageId(), e);
        }
    }

    private String resolveFileId(GroupMessageReceivedEvent event) {
        return event.isStaticSticker() ? event.sticker().fileId() : event.photo().fileId();
    }

    private ChatImageEntity toEntity(GroupMessageReceivedEvent event, byte[] imageData) {
        boolean sticker = event.isStaticSticker();
        ChatImageEntity entity = new ChatImageEntity();
        entity.setImageType(sticker ? "sticker" : "photo");
        entity.setFileUniqueId(sticker ? event.sticker().fileUniqueId() : event.photo().fileUniqueId());
        entity.setChatId(event.chatId());
        entity.setTelegramUserId(event.userId());
        entity.setTelegramMessageId(event.telegramMessageId());
        if (sticker) {
            entity.setEmoji(event.sticker().emoji());
            entity.setSetName(event.sticker().setName());
            entity.setWidth(event.sticker().width());
            entity.setHeight(event.sticker().height());
        } else {
            entity.setWidth(event.photo().width());
            entity.setHeight(event.photo().height());
        }
        entity.setImageData(imageData);
        entity.setFileSize(imageData.length);
        entity.setSentAt(event.sentAt());
        return entity;
    }
}
