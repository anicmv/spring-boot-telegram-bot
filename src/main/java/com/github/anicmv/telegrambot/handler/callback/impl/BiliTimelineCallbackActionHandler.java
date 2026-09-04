package com.github.anicmv.telegrambot.handler.callback.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.anicmv.telegrambot.annotation.BotCallback;
import com.github.anicmv.telegrambot.handler.callback.CallbackActionHandler;
import com.github.anicmv.telegrambot.constant.BotConstant;
import com.github.anicmv.telegrambot.utils.HttpUtil;
import com.github.anicmv.telegrambot.messenger.Messenger;
import com.github.anicmv.telegrambot.model.BotContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description B 站番剧时间线回调处理器。
 */
@BotCallback(BotConstant.CALLBACK_ACTION_BILI)
@Component
public class BiliTimelineCallbackActionHandler implements CallbackActionHandler {

    private final Messenger messenger;

    public BiliTimelineCallbackActionHandler(Messenger messenger) {
        this.messenger = messenger;
    }

    @Override
    public void execute(BotContext context, String payload) {
        String api = "GM".equalsIgnoreCase(payload) ? BotConstant.API_BILI_GM : BotConstant.API_BILI_RM;
        String content = timeline(api);
        String inlineMessageId = context.callbackQuery().getInlineMessageId();
        if (inlineMessageId != null && !inlineMessageId.isBlank()) {
            messenger.editInlineMessageText(inlineMessageId, "<pre>" + content + "</pre>", "HTML");
        } else {
            messenger.sendText(context.chatId(), content);
        }
    }

    private String timeline(String api) {
        String response = HttpUtil.get(api, Map.of(BotConstant.HEADER_USER_AGENT, BotConstant.USER_AGENT));
        if (response == null || response.isBlank()) {
            return "获取 B 站时间线失败。";
        }
        JSONObject biliJson = JSONUtil.parseObj(response);
        Integer code = biliJson.getInt("code", -1);
        if (!Integer.valueOf(0).equals(code)) {
            return "B 站接口返回异常。";
        }
        JSONArray resultArray = biliJson.getJSONArray("result");
        if (resultArray == null || resultArray.isEmpty()) {
            return "没有找到时间线数据。";
        }
        JSONObject episodesObj = resultArray.getJSONObject(0);
        JSONArray episodes = episodesObj.getJSONArray("episodes");
        if (episodes == null || episodes.isEmpty()) {
            return "没有找到剧集数据。";
        }

        String headerTime = "Time";
        String headerTitle = "Title";
        int maxTimeLen = headerTime.length();
        int maxTitleLen = headerTitle.length();
        List<String[]> rows = new ArrayList<>();
        for (Object obj : episodes) {
            JSONObject episode = JSONUtil.parseObj(obj);
            String time = episode.getStr("pub_time", "");
            String title = episode.getStr("title", "") + " -> (" + episode.getStr("pub_index", "") + ")";
            maxTimeLen = Math.max(maxTimeLen, time.length());
            maxTitleLen = Math.max(maxTitleLen, title.length());
            rows.add(new String[]{time, title});
        }
        maxTimeLen += 2;
        maxTitleLen += 2;
        String format = "%-" + maxTimeLen + "s  %-" + maxTitleLen + "s%n";
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(format, headerTime, headerTitle));
        builder.append("-".repeat(maxTimeLen + 2 + maxTitleLen)).append("\n");
        for (String[] row : rows) {
            builder.append(String.format(format, row[0], row[1]));
        }
        return builder.toString();
    }
}
