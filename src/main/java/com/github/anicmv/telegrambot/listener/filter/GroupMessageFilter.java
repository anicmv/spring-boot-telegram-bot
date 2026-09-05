package com.github.anicmv.telegrambot.listener.filter;

import com.github.anicmv.telegrambot.event.MessageReceivedEvent;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 群消息记录过滤器。所有过滤器按 {@code @Order} 顺序组成过滤链，
 * 任一过滤器拒绝即不记录该消息。新增过滤规则只需实现本接口并注册为 Bean。
 */
public interface GroupMessageFilter {

    /**
     * 判断消息是否通过当前过滤器。
     *
     * @param event 群消息事件，保证非 null
     * @return true 表示继续走链，false 表示不记录
     */
    boolean accept(MessageReceivedEvent event);
}
