package com.github.anicmv.telegrambot.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DouyinVideoServiceTest {

    @Test
    void shouldExtractFirstDouyinUrlFromShareText() {
        assertEquals(
                "https://v.douyin.com/example/",
                DouyinVideoService.extractFirstUrl("复制这条链接 https://v.douyin.com/example/ 打开抖音")
        );
    }

    @Test
    void shouldPrefixSchemeWhenUrlHasNoScheme() {
        assertEquals(
                "https://v.douyin.com/example/",
                DouyinVideoService.extractFirstUrl("v.douyin.com/example/")
        );
    }

    @Test
    void shouldTrimChinesePunctuationAfterUrl() {
        assertEquals(
                "https://v.douyin.com/example/",
                DouyinVideoService.extractFirstUrl("https://v.douyin.com/example/，")
        );
    }
}
