package com.github.anicmv.telegrambot.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 执行本地转换命令的边界，便于隔离测试与生产进程管理。
 */
@FunctionalInterface
public interface CommandExecutor {

    CommandResult execute(List<String> command, Path workingDirectory, Duration timeout) throws Exception;

    record CommandResult(int exitCode, String output) {
        public boolean successful() {
            return exitCode == 0;
        }
    }
}
