package com.github.anicmv.telegrambot.constant;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description 机器人常量。
 */
public final class BotConstant {

    private BotConstant() {
    }

    public static final String HEADER_USER_AGENT = "User-Agent";
    public static final String HEADER_REFERER = "Referer";
    public static final String USER_AGENT = "Mozilla/5.0 TelegramBot";
    public static final String KFC_REFERER = "https://www.kfc.com.cn/";

    public static final String CMD_START = "/start";
    public static final String CMD_HELP = "/help";
    public static final String CMD_PING = "/ping";
    public static final String CMD_TEST = "/test";
    public static final String CMD_AI = "/ai";
    public static final String CMD_DOUYIN = "/douyin";
    public static final String CMD_KFC = "/kfc";
    public static final String CMD_MAF = "/maf";
    public static final String CMD_CHP = "/chp";
    public static final String CMD_PYQ = "/pyq";
    public static final String CMD_DU = "/du";
    public static final String CMD_INLINE = "/inline";
    public static final String CMD_MATCHMAKER_REGISTER = "/matchmaker_register";
    public static final String CMD_VIDEO = "/video";
    public static final String CMD_SEARCHIMG = "/searchimg";
    public static final String CMD_PROFILE = "/profile";
    public static final String CMD_PACK = "/pack";
    public static final String CMD_STICKER = "/sticker";
    public static final String CMD_AVATARS = "/avatars";
    public static final String CMD_INFO = "/info";
    public static final String CMD_ANIME = "/anime";
    public static final String CMD_HOLIDAY = "/holiday";

    public static final String CALLBACK_ACTION_PING = "PING";
    /** /profile 白名单授权按钮，payload 形如 Y:<userId> / N:<userId>。 */
    public static final String CALLBACK_ACTION_PROFILE_AUTH = "PROFILE_AUTH";
    public static final String CALLBACK_ACTION_NOOP = "NOOP";
    public static final String CALLBACK_ACTION_BILI = "CALLBACK_BILI";
    public static final String CALLBACK_ACTION_XP_PREFIX = "XP_";
    public static final String CALLBACK_BILI_GM = "CALLBACK_BILI:GM";
    public static final String CALLBACK_BILI_RM = "CALLBACK_BILI:RM";
    public static final String CALLBACK_XP_BS = "XP_BS";
    public static final String CALLBACK_XP_JK = "XP_JK";
    public static final String CALLBACK_XP_HS = "XP_HS";
    public static final String CALLBACK_XP_DEFAULT = "XP_DEFAULT";

    public static final String INLINE_ID_RANDOM_ECY = "N_1";
    public static final String INLINE_ID_XP = "N_2";
    public static final String INLINE_ID_BILI = "N_4";
    public static final String INLINE_ID_KFC = "N_8";
    public static final String INLINE_ID_TOP_PROGRAMMING = "N_9";
    public static final String INLINE_ID_CHP = "N_10";
    public static final String INLINE_ID_PYQ = "N_11";
    public static final String INLINE_ID_DU = "N_12";
    public static final String INLINE_ID_MATCHMAKER = "N_13";
    public static final String INLINE_ID_DB = "N_3";
    public static final String INLINE_ID_AI = "N_14";
    public static final String INLINE_ID_DOUYIN = "N_15";
    public static final String INLINE_ID_ARTICLE = "N_99";

    public static final String API_KFC = "https://api.shadiao.pro/kfc";
    public static final String API_CHP = "https://api.shadiao.pro/chp";
    public static final String API_PYQ = "https://api.shadiao.pro/pyq";
    public static final String API_DU = "https://api.shadiao.pro/du";
    public static final String API_MOYU = "https://api.vvhan.com/api/moyu";
    public static final String API_BILI_GM = "https://api.bilibili.com/pgc/web/timeline?types=4&before=0&after=0";
    public static final String API_BILI_RM = "https://api.bilibili.com/pgc/web/timeline?types=1&before=0&after=0";
    public static final String API_XP_BS = "https://acg.suyanw.cn/whitesilk/random.php";
    public static final String API_XP_JK = "https://api.suyanw.cn/api/jk/";
    public static final String API_XP_HS = "https://api.suyanw.cn/api/hs/";
    public static final String API_XP_DEFAULT = "https://acg.suyanw.cn/meizi/random.php";
    public static final String API_RANDOM_ECY_1 = "https://moe.jitsu.top/img/";
    public static final String API_RANDOM_ECY_2 = "https://www.loliapi.com/bg/";
    public static final String API_TIOBE = "https://www.tiobe.com/tiobe-index/";
    public static final String API_DB_SEARCH = "https://frodo.douban.com/api/v2/search/weixin";
    public static final String DB_API_KEY = "0ac44ae016490db2204ce0a042db2916";
    public static final String DB_AUTHORIZATION = "Bearer 822fe716a9a2c696868fb543fce853c2";
}
