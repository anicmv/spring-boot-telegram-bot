package com.github.anicmv.telegrambot.listener.filter;

import com.github.anicmv.telegrambot.config.BotProperties;
import com.github.anicmv.telegrambot.event.MessageReceivedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 过滤链第三环：校验记录总开关与群白名单配置。
 */
@Order(30)
@Component
@Qualifier("recordChain")
public class RecordConfigFilter implements GroupMessageFilter {

    private final BotProperties properties;

    public RecordConfigFilter(BotProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean accept(MessageReceivedEvent event) {
        BotProperties.Profile profileProps = properties.getProfile();
        return profileProps.isRecordEnabled()
                && profileProps.getRecordGroupIds().contains(event.chatId());
    }
}
