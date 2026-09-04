package com.github.anicmv.telegrambot.listener.filter;

import com.github.anicmv.telegrambot.event.GroupMessageReceivedEvent;
import com.github.anicmv.telegrambot.handler.command.BotCommandRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/9/5
 * @description 过滤链第五环：拒绝 bot 命令消息（以 / 开头且命中已注册命令），
 * 命令触发语不属于用户画像语料。未注册的 / 开头文本视为普通发言放行。
 */
@Order(50)
@Component
@Qualifier("recordChain")
public class CommandMessageFilter implements GroupMessageFilter {

    private final BotCommandRegistry botCommandRegistry;

    public CommandMessageFilter(BotCommandRegistry botCommandRegistry) {
        this.botCommandRegistry = botCommandRegistry;
    }

    @Override
    public boolean accept(GroupMessageReceivedEvent event) {
        String text = event.text();
        if (text == null || !text.startsWith("/")) {
            return true;
        }
        String command = text.split("\\s+")[0];
        return botCommandRegistry.find(command) == null;
    }
}
