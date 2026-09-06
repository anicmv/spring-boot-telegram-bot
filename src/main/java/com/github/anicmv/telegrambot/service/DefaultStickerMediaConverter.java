package com.github.anicmv.telegrambot.service;

import com.github.anicmv.telegrambot.config.BotProperties;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.stickers.Sticker;

/**
 * 贴纸格式转换：WebP 使用 ImageIO/TwelveMonkeys，TGS 使用 lottie-converter，WebM 使用 ffmpeg。
 */
@Log4j2
@Component
public class DefaultStickerMediaConverter implements StickerMediaConverter {

    private final CommandExecutor commandExecutor;
    private final BotProperties properties;

    public DefaultStickerMediaConverter(CommandExecutor commandExecutor, BotProperties properties) {
        this.commandExecutor = commandExecutor;
        this.properties = properties;
    }

    @Override
    public ConversionResult convert(Sticker sticker, Path source, Path workDirectory) throws Exception {
        if (source == null || !Files.isRegularFile(source) || Files.size(source) == 0) {
            throw new IOException("贴纸源文件为空");
        }
        if (Boolean.TRUE.equals(sticker.getIsAnimated())) {
            return runCommand(List.of(properties.getPack().getLottieConverterCommand(), "--output",
                    workDirectory.resolve("converted.gif").toString(), source.toString()),
                    workDirectory.resolve("converted.gif"), workDirectory, ".gif");
        }
        if (Boolean.TRUE.equals(sticker.getIsVideo())) {
            return runCommand(List.of(properties.getPack().getFfmpegCommand(), "-y", "-i", source.toString(),
                    "-vf", "fps=15,scale=256:-1:flags=lanczos", workDirectory.resolve("converted.gif").toString()),
                    workDirectory.resolve("converted.gif"), workDirectory, ".gif");
        }
        Path target = workDirectory.resolve("converted.png");
        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null || !ImageIO.write(image, "png", target.toFile())) {
            throw new IOException("WebP 图片解码或 PNG 写入失败");
        }
        if (Files.size(target) == 0) {
            throw new IOException("PNG 输出为空");
        }
        return new ConversionResult(target, ".png");
    }

    private ConversionResult runCommand(List<String> command, Path output, Path workDirectory, String extension)
            throws Exception {
        Duration timeout = Duration.ofSeconds(properties.getPack().getConversionTimeoutSeconds());
        CommandExecutor.CommandResult result = commandExecutor.execute(command, workDirectory, timeout);
        if (!result.successful()) {
            throw new IOException("转换命令失败（退出码 " + result.exitCode() + "）: " + result.output());
        }
        if (!Files.isRegularFile(output) || Files.size(output) == 0) {
            throw new IOException("转换命令未生成有效输出: " + output);
        }
        return new ConversionResult(output, extension);
    }
}
