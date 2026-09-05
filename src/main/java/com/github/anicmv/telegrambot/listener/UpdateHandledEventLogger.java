package com.github.anicmv.telegrambot.listener;

import com.github.anicmv.telegrambot.event.UpdateHandledEvent;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 事件监听器，记录更新处理后的观测日志。
 */
@Log4j2
@Component
public class UpdateHandledEventLogger {

    @EventListener
    public void onUpdateHandled(UpdateHandledEvent event) {
        log.debug("Update handled: kind={}, chatId={}", event.updateKind(), event.chatId());
    }
}
