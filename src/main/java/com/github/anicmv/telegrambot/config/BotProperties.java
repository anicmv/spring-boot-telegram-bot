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
        private int batchMessageLimit = 100;

        /**
         * /profile 是否允许通过 @username 直接查他人画像。
         */
        private boolean allowQueryOthers = false;

        /**
         * /profile 命令使用白名单（Telegram 用户 ID）；空集合表示不限制。
         */
        private Set<Long> allowUserIds = new HashSet<>();

        /**
         * 记录到画像表的模型名。
         */
        private String model = "deepseek-chat";

        /**
         * analyzeAll 并行分析的并发用户数（每人一次 LLM 调用）。
         */
        private int analysisConcurrency = 4;

        /**
         * 画像分析系统提示词。
         */
        private String analysisPrompt = """
                请基于该账号的聊天记录，产出一份赛博群友人物画像报告。不要做成枯燥的数据分析，
                要用乐子人视角、梗感文风，挖掘 TA 的线上人设、说话习惯、黑话体系、反差性格、群内定位、行为名场面，
                像群友背地里偷偷扒人档案一样活泼搞笑，禁止官方书面腔。每个论断都必须有聊天记录佐证，鼓励直接引用原话。
                要求：
                1. 只输出一个 JSON 对象，不要输出任何解释文字或代码块标记。
                2. JSON 字段固定为：
                   - summary：字符串，一句话人设（40-80 字），高密度堆叠人设标签，毒舌但贴切
                   - report：字符串，600-900 字的画像正文，分 3-4 段（段落间用空行分隔），依次覆盖：
                     发言语言特征与黑话体系、兴趣图谱与行为名场面、性格反差、群内定位与灵魂总结
                3. 新记录与旧画像矛盾时以新证据为准；某方面没有新信息时保留旧结论。
                4. 不得编造聊天记录之外的信息；记录不足时 summary 如实简短描述、report 只写已有线索。
                """;
    }

}
