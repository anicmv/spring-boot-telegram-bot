package com.github.anicmv.telegrambot.listener.filter;

import com.github.anicmv.telegrambot.event.MessageReceivedEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description 仅放行静态图片类消息：静态贴纸（webp）或用户发送的图片，
 * 属于图片记录链专属过滤器，不带 recordChain 限定符，不会进入消息记录监听器的过滤链。
 */
@Order(40)
@Component
public class StaticImageFilter implements GroupMessageFilter {

    @Override
    public boolean accept(MessageReceivedEvent event) {
        return event.isStaticSticker() || event.isPhotoMessage();
    }
}
