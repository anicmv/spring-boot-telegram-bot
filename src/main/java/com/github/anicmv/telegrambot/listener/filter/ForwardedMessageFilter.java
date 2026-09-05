package com.github.anicmv.telegrambot.listener.filter;

import com.github.anicmv.telegrambot.event.MessageReceivedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 过滤链第四环：拒绝转发消息，仅记录用户原创发言。
 */
@Order(40)
@Component
@Qualifier("recordChain")
public class ForwardedMessageFilter implements GroupMessageFilter {

    @Override
    public boolean accept(MessageReceivedEvent event) {
        return !event.forwarded();
    }
}
