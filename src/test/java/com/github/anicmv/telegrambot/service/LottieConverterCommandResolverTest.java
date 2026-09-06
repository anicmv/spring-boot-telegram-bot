package com.github.anicmv.telegrambot.service;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LottieConverterCommandResolverTest {

    @Test
    void shouldExtractBundledConverterWithSiblingFiles() throws Exception {
        LottieConverterCommandResolver resolver = new LottieConverterCommandResolver();
        try {
            Path script = resolver.resolve();

            assertTrue(script.isAbsolute());
            assertTrue(Files.isRegularFile(script));
            assertTrue(Files.isRegularFile(script.resolveSibling("lottie_common.sh")));
            assertTrue(Files.isRegularFile(script.resolveSibling("lottie_to_png")));
            assertEquals(script, resolver.resolve());
        } finally {
            resolver.close();
        }
    }
}
