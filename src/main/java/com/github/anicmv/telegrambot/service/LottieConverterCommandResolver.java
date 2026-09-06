package com.github.anicmv.telegrambot.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 将 classpath 中的 lottie-converter 运行时文件解包到文件系统，供 shell 脚本执行。
 * Spring Boot fat jar 内的资源不是可直接传给 ProcessBuilder 的普通文件。
 */
@Component
public class LottieConverterCommandResolver implements AutoCloseable {

    static final String RESOURCE_ROOT = "lottie-converter/bin";
    private static final List<String> REQUIRED_FILES = List.of(
            "lottie_common.sh",
            "lottie_to_gif.sh",
            "lottie_to_png"
    );
    private static final Set<PosixFilePermission> EXECUTABLE_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE
    );

    private Path extractedDirectory;

    @PreDestroy
    public void destroy() {
        close();
    }

    /**
     * 应用启动时提前解包并校验内置转换器，避免第一次贴纸转换才暴露部署问题。
     */
    @PostConstruct
    public synchronized void initialize() throws IOException {
        resolve();
    }

    /**
     * 返回可由 {@code bash} 执行的 lottie_to_gif.sh 文件路径。
     * 同步初始化确保并发转换不会读取未完成的解包目录。
     */
    public synchronized Path resolve() throws IOException {
        verifySupportedPlatform();
        if (extractedDirectory != null) {
            Path script = extractedDirectory.resolve("lottie_to_gif.sh");
            if (Files.isRegularFile(script)) {
                return script;
            }
            deleteExtractedDirectory();
        }

        Path directory = Files.createTempDirectory("lottie-converter-");
        try {
            for (String fileName : REQUIRED_FILES) {
                Path target = directory.resolve(fileName);
                ClassPathResource resource = new ClassPathResource(RESOURCE_ROOT + "/" + fileName);
                if (!resource.exists()) {
                    throw new IOException("内置 lottie-converter 资源不存在: " + fileName);
                }
                try (InputStream input = resource.getInputStream()) {
                    Files.copy(input, target);
                }
                makeExecutable(target);
            }
            extractedDirectory = directory;
            return directory.resolve("lottie_to_gif.sh");
        } catch (Exception e) {
            deleteDirectory(directory);
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("解包内置 lottie-converter 失败", e);
        }
    }

    @Override
    public synchronized void close() {
        deleteExtractedDirectory();
    }

    private void verifySupportedPlatform() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        boolean macArm = (os.contains("mac") || os.contains("darwin"))
                && (arch.equals("aarch64") || arch.equals("arm64"));
        if (!macArm) {
            throw new IOException("内置 lottie-converter 仅支持 macOS ARM，当前平台: "
                    + os + "/" + arch + "；请配置外部 lottie-converter 命令");
        }
    }
    private void makeExecutable(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, EXECUTABLE_PERMISSIONS);
        } catch (UnsupportedOperationException e) {
            if (!file.toFile().setExecutable(true, false)) {
                throw new IOException("无法设置内置转换器执行权限: " + file, e);
            }
        }
    }

    private void deleteExtractedDirectory() {
        if (extractedDirectory != null) {
            deleteDirectory(extractedDirectory);
            extractedDirectory = null;
        }
    }

    private void deleteDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }
}
