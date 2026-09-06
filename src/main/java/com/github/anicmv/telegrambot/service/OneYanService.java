package com.github.anicmv.telegrambot.service;

import com.github.anicmv.telegrambot.config.HolidayProperties;
import com.github.anicmv.telegrambot.utils.HttpUtil;
import org.springframework.stereotype.Service;

/**
 * 获取一言纯文本。该接口返回普通正文，不按 JSON 或 Telegram HTML 解析。
 */
@Service
public class OneYanService {

    private static final int MAX_TEXT_LENGTH = 500;

    private final HolidayProperties properties;

    public OneYanService(HolidayProperties properties) {
        this.properties = properties;
    }

    public String fetch() {
        return extract(HttpUtil.get(properties.getOneYanApi()));
    }

    String extract(String response) {
        String fallback = properties.getOneYanFallback();
        if (response == null || response.isBlank()) {
            return fallback;
        }
        String text = response.strip();
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }
}
