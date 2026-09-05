package com.github.anicmv.telegrambot.listener.filter;

import com.github.anicmv.telegrambot.event.MessageReceivedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 过滤链第二环：拒绝 bot 发送的消息，避免机器人自身发言污染用户画像语料。
 */
@Order(20)
@Component
@Qualifier("recordChain")
public class BotMessageFilter implements GroupMessageFilter {

    @Override
    public boolean accept(MessageReceivedEvent event) {
        return !event.senderIsBot();
    }
}
