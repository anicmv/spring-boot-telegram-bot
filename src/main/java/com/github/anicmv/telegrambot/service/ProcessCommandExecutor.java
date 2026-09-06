package com.github.anicmv.telegrambot.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * 基于 ProcessBuilder 的外部命令执行器。参数按 token 传递，不经过 shell。
 */
@Log4j2
@Component
public class ProcessCommandExecutor implements CommandExecutor {

    private static final int OUTPUT_LIMIT = 4000;

    @Override
    public CommandResult execute(List<String> command, Path workingDirectory, Duration timeout)
            throws IOException, InterruptedException {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("转换命令不能为空");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("转换超时时间必须为正数");
        }

        Path logFile = Files.createTempFile("sticker-command-", ".log");
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile());
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }

        Process process = null;
        try {
            process = processBuilder.start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                throw new IOException("命令执行超时: " + command.getFirst());
            }
            return new CommandResult(process.exitValue(), readOutput(logFile));
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            Files.deleteIfExists(logFile);
        }
    }

    private String readOutput(Path logFile) throws IOException {
        long size = Files.size(logFile);
        try (InputStream input = Files.newInputStream(logFile)) {
            long remaining = Math.max(0, size - OUTPUT_LIMIT);
            while (remaining > 0) {
                long skipped = input.skip(remaining);
                if (skipped == 0) {
                    if (input.read() == -1) {
                        break;
                    }
                    skipped = 1;
                }
                remaining -= skipped;
            }
            return new String(input.readNBytes(OUTPUT_LIMIT), StandardCharsets.UTF_8);
        }
    }
}
