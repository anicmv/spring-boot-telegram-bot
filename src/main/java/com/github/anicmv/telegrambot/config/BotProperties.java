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
         * 记录到画像表的模型名。
         */
        private String model = "deepseek-chat";

        /**
         * 画像分析系统提示词。
         */
        private String analysisPrompt = """
                请基于该账号的聊天记录，产出一份赛博群友人物画像报告。不要做成枯燥的数据分析，
                要用乐子人视角、梗感文风，挖掘 TA 的线上人设、说话习惯、黑话体系、反差性格、群内定位、行为名场面，
                像群友背地里偷偷扒人档案一样活泼搞笑，禁止官方书面腔。每个论断都必须有聊天记录佐证。
                要求：
                1. 只输出一个 JSON 对象，不要输出任何解释文字或代码块标记。
                2. JSON 字段固定为（对应画像报告七个板块）：
                   - summary：字符串，150-250 字的“灵魂总结”，文风放飞、敢下暴论但有据可依
                   - interests：字符串数组，兴趣图谱标签，用梗词/黑话命名（如“深夜emo音乐”“原神启动”）
                   - personality：对象，固定包含以下键，值均为一句有梗的短描述：
                     - 人设标签：TA 在群里的外在人设
                     - 语言特征：说话习惯、口头禅、黑话体系
                     - 行为名场面：高频行为与最有画面感的时刻
                     - 性格反差：线上人设与真实性格的反差
                     - 群内定位：TA 在群里扮演的角色（如“气氛组”“鉴游师”“人形复读机”）
                   - active_hours：字符串，活跃时段描述，用梗表达（如“深夜发癫时段 23:00-03:00”）
                   - frequent_topics：字符串数组，高频话题与爱用的梗
                3. 新记录与旧画像矛盾时以新证据为准；某方面没有新信息时保留旧结论。
                4. 不得编造聊天记录之外的信息；记录内容不足时对应字段填空字符串/空数组/空对象。
                """;
    }

}
