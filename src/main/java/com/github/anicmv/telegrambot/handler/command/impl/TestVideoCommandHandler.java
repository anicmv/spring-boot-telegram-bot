package com.github.anicmv.telegrambot.handler.command.impl;

import com.github.anicmv.telegrambot.annotation.BotCommand;
import com.github.anicmv.telegrambot.handler.command.BotCommandHandler;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author anicmv
 * @date 2026/5/2 21:06
 * @description /test 命令处理器，上传本地测试视频。
 */
@BotCommand(BotConstant.CMD_TEST)
@Component
public class TestVideoCommandHandler implements BotCommandHandler {

    private final Messenger messenger;
    private final String testVideoPath;

    public TestVideoCommandHandler(Messenger messenger,
                                   @Value("${bot.test.video-path:}") String testVideoPath) {
        this.messenger = messenger;
        this.testVideoPath = testVideoPath == null ? "" : testVideoPath.strip();
    }

    @Override
    public void execute(BotContext context) {
        if (testVideoPath.isBlank()) {
            replyText(context, "未配置 bot.test.video-path。");
            return;
        }
        if (!Files.isRegularFile(Path.of(testVideoPath))) {
            replyText(context, "看什么看：" + testVideoPath);
            return;
        }
        if (context.message() != null && context.message().getMessageId() != null) {
            messenger.sendReplyVideoByPath(context.chatId(), context.message().getMessageId(), testVideoPath, "看什么看");
            return;
        }
        messenger.sendVideoByPath(context.chatId(), testVideoPath, "看什么看");
    }

    private void replyText(BotContext context, String text) {
        if (context.message() != null && context.message().getMessageId() != null) {
            messenger.sendReplyText(context.chatId(), context.message().getMessageId(), text);
            return;
        }
        messenger.sendText(context.chatId(), text);
    }
}
