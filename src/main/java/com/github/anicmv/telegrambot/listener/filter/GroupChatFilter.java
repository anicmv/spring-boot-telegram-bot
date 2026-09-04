package com.github.anicmv.telegrambot.listener.filter;

import com.github.anicmv.telegrambot.event.GroupMessageReceivedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 过滤链第一环：仅放行群聊（group/supergroup）消息。
 */
@Order(10)
@Component
@Qualifier("recordChain")
public class GroupChatFilter implements GroupMessageFilter {

    @Override
    public boolean accept(GroupMessageReceivedEvent event) {
        return event.isGroupChat();
    }
}
