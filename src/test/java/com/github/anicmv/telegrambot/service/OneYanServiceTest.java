package com.github.anicmv.telegrambot.service;

import com.github.anicmv.telegrambot.config.HolidayProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OneYanServiceTest {

    @Test
    void shouldReturnTrimmedText() {
        OneYanService service = new OneYanService(properties());

        assertEquals("今天也要开心。", service.extract("  今天也要开心。  "));
    }

    @Test
    void blankTextShouldUseFallback() {
        OneYanService service = new OneYanService(properties());

        assertEquals("fallback", service.extract(" \n "));
        assertEquals("fallback", service.extract(null));
    }

    private HolidayProperties properties() {
        HolidayProperties properties = new HolidayProperties();
        properties.setOneYanFallback("fallback");
        return properties;
    }
}
