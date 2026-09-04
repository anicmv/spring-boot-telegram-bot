package com.github.anicmv.telegrambot.constant;

/**
 * @author anicmv
 * @date 2026/3/18
 * @description XP 回调枚举。
 */
public enum XpCategory {
    XP_JK(BotConstant.CALLBACK_XP_JK, "JK", BotConstant.API_XP_JK),
    XP_HS(BotConstant.CALLBACK_XP_HS, "黑丝", BotConstant.API_XP_HS),
    XP_BS(BotConstant.CALLBACK_XP_BS, "白丝", BotConstant.API_XP_BS),
    XP_DEFAULT(BotConstant.CALLBACK_XP_DEFAULT, "妹子", BotConstant.API_XP_DEFAULT);

    private final String callback;
    private final String description;
    private final String api;

    XpCategory(String callback, String description, String api) {
        this.callback = callback;
        this.description = description;
        this.api = api;
    }

    public String getCallback() {
        return callback;
    }

    public String getDescription() {
        return description;
    }

    public String getApi() {
        return api;
    }

    public static XpCategory fromCallback(String callback) {
        for (XpCategory xp : values()) {
            if (xp.getCallback().equals(callback)) {
                return xp;
            }
        }
        return XP_DEFAULT;
    }
}
