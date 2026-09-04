package com.github.anicmv.telegrambot.listener.filter;

import com.github.anicmv.telegrambot.event.GroupMessageReceivedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/9/5
 * @description 过滤链第六环：拒绝 inline mode 代发消息（via_bot 非空，含任意 bot），
 * 用户只是选取了结果，并非自发内容，不属于用户画像语料。
 */
@Order(60)
@Component
@Qualifier("recordChain")
public class InlineModeMessageFilter implements GroupMessageFilter {

    @Override
    public boolean accept(GroupMessageReceivedEvent event) {
        return !event.viaBot();
    }
}
