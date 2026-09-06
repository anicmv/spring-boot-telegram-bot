package com.github.anicmv.telegrambot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

/**
 * @author anicmv
 * @date 2026/3/15 21:27
 * @description Bot 配置属性，映射 bot.telegram 前缀。
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "bot.telegram")
public class BotProperties {

    private String token;
    private String username;
    private Long channelId;
    private Network network = new Network();
    private Ai ai = new Ai();
    private Profile profile = new Profile();
    private Pack pack = new Pack();
    private KeywordNotify keywordNotify = new KeywordNotify();

    @Setter
    @Getter
    public static class Pack {
        /**
         * 单次打包最多下载的贴纸数（Telegram 贴纸包上限 120）。
         */
        private int maxStickers = 120;

        /**
         * zip 体积上限（字节），超出截断；需小于 Bot API 50MB 上传限制。
         */
        private long maxZipBytes = 49L * 1024 * 1024;

        /**
         * TGS 转 GIF 命令；为空时自动使用 classpath 中内置的 lottie-converter。
         * 如需使用外部版本，可配置脚本的绝对路径或 PATH 命令。
         */
        private String lottieConverterCommand = "";

        /**
         * WebM 转 GIF 命令，默认从 PATH 查找 ffmpeg。
         */
        private String ffmpegCommand = "ffmpeg";

        /**
         * 单张贴纸转码超时，单位秒。
         */
        private long conversionTimeoutSeconds = 180L;
    }

    @Setter
    @Getter
    public static class Network {
        /**
         * 连接 Telegram API 的超时时间，单位秒。
         */
        private long connectTimeoutSeconds = 30L;

        /**
         * 读取 Telegram API 响应的超时时间，单位秒。
         */
        private long readTimeoutSeconds = 60L;

        /**
         * 写入 Telegram API 请求的超时时间，单位秒。
         */
        private long writeTimeoutSeconds = 60L;
    }

    @Setter
    @Getter
    public static class Ai {
        private String systemPrompt = "你是一个小男娘，始终使用中文对话，用可爱的语气与我对话。";
        private Set<Long> blacklistUserIds = new HashSet<>();
        private boolean autoDeleteEnabled = true;
        private long autoDeleteDelaySeconds = 30L;

        /**
         * /ai 对话是否开启百炼联网搜索（enable_search）；仅影响对话链路，画像分析不受影响。
         */
        private boolean webSearchEnabled = true;
    }

    @Setter
    @Getter
    public static class Profile {
        /**
         * 群聊消息记录总开关。
         */
        private boolean recordEnabled = false;

        /**
         * 群白名单，仅记录这些群的消息。
         */
        private Set<Long> recordGroupIds = new HashSet<>();

        /**
         * 单人单轮分析的最大消息条数。
         */
        private int batchMessageLimit = 1000;

        /**
         * /profile 是否每次现场强制重新生成画像（忽略存量画像，从最早消息重新分析一轮）。
         * 画像格式调整期临时开启，稳定后应关闭，改由每晚跑批滚动更新、/profile 直接读库。
         */
        private boolean regenerateOnQuery = true;

        /**
         * /profile 授权管理员（Telegram 用户 ID）：仅其点击授权按钮生效，且 admin 免白名单直接使用 /profile。
         * 普通用户白名单已数据库化（profile_allow_user 表），由管理员通过按钮授权落库。
         */
        private Set<Long> adminUserIds = new HashSet<>();

        /**
         * 画像输出后是否延时清理命令消息与画像消息（减少群聊噪音）。
         */
        private boolean autoDeleteEnabled = true;

        /**
         * 画像产出后（画像消息 + 命令消息）自动清理延迟秒数。
         */
        private long autoDeleteDelaySeconds = 120L;

        /**
         * 授权申请按钮消息发出后的自动清理延迟秒数（提醒管理员尽快处理）。
         */
        private long approvalRequestDeleteSeconds = 30L;

        /**
         * analyzeAll 并行分析的并发用户数（每人一次 LLM 调用）。
         */
        private int analysisConcurrency = 4;

        /**
         * 画像分析系统提示词。
         */
        private String analysisPrompt = """
                输入包含该账号的身份信息（用户名/昵称/简介）与聊天记录，请基于两者产出一份赛博群友人物画像报告。
                身份资料只是线索彩蛋，不可仅凭昵称简介臆造人设，主要论据仍须来自聊天记录。不要做成枯燥的数据分析，
                要用乐子人视角、梗感文风，挖掘 TA 的线上人设、说话习惯、黑话体系、反差性格、群内定位、行为名场面，
                像群友背地里偷偷扒人档案一样活泼搞笑，禁止官方书面腔。每个论断都必须有聊天记录佐证。
                要求：
                1. 只输出一个 JSON 对象，不要输出任何解释文字或代码块标记。
                2. JSON 字段固定为：
                   - summary：字符串，画像正文，150-200 字，单段连贯成文，高密度堆叠人设标签，毒舌但贴切，
                     浓缩覆盖：发言语言特征与黑话、兴趣与行为名场面、性格反差、群内定位
                3. 新记录与旧画像矛盾时以新证据为准；某方面没有新信息时保留旧结论。
                4. 不得编造聊天记录之外的信息；记录不足时 summary 如实简短描述、只写已有线索。
                """;
    }

    @Setter
    @Getter
    public static class KeywordNotify {
        /**
         * 关键词通知功能总开关。
         */
        private boolean enabled = false;

        /**
         * 监听的关键词列表。
         */
        private Set<String> keywords = new HashSet<>();

        /**
         * 接收通知的 Telegram 用户 ID。
         */
        private Long notifyUserId;

        /**
         * 监听的群 ID 白名单（空表示监听所有群）。
         */
        private Set<Long> groupIds = new HashSet<>();
    }

}
