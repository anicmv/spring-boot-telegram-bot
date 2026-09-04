package com.github.anicmv.telegrambot.utils;

import com.github.anicmv.telegrambot.constant.BotConstant;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.util.Map;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 沙雕文案接口工具类。
 */
public final class ShadiaoCopywritingUtil {

    private ShadiaoCopywritingUtil() {
    }

    public static String fetchText(String api, String fallbackText) {
        String response = HttpUtil.get(api, Map.of(
                BotConstant.HEADER_USER_AGENT, BotConstant.USER_AGENT,
                BotConstant.HEADER_REFERER, BotConstant.KFC_REFERER
        ));
        if (response == null || response.isBlank()) {
            return fallbackText;
        }
        JSONObject obj = JSONUtil.parseObj(response);
        Object text = obj.getByPath("data.text");
        if (text == null) {
            return fallbackText;
        }
        return text.toString();
    }
}
