# spring-boot-telegram-bot

基于 `Spring Boot 4.1.1 + JDK 25 + TelegramBots 10.2.1` 的 Telegram 机器人项目，当前使用 **Long Polling** 模式。

项目目标：从一开始就支持可扩展的文本、命令、图片、按钮回调、Inline 查询、Chosen Inline Query，并内置群聊消息记录与 AI 用户画像能力。

## 技术栈

- Spring Boot `4.1.1`，Java `25`
- Telegram Bots `10.2.1`（Long Polling）
- Spring AI `2.0.1`（OpenAI 兼容协议，对接百炼/DeepSeek，用于 `/ai` 对话与画像生成）
- MyBatis-Plus + MySQL（消息落库、画像、白名单；`schema-mysql.sql` 启动自动建表）
- xxl-job（画像每晚跑批调度）
- WebClient、Jsoup（编程语言榜抓取）、Hutool、TwelveMonkeys WebP ImageIO、yt-dlp（YouTube / Instagram / 小红书 视频下载）
- `/pack` 转换依赖：`lottie-converter` 的 `lottie_to_gif.sh` + `gifski`（TGS→GIF），系统 `ffmpeg`（WebM→GIF）
- Gradle（Groovy DSL）

## 已实现能力

### 命令

- `/start`：注册用户并发送欢迎文本 + `Ping` 按钮
- `/help`：帮助说明（自动汇总所有命令的 description）
- `/ping`：连通性测试（自动删除触发消息与回复）
- `/ai`：AI 对话；私聊直接 `/ai 问题`，群聊必须 `@机器人` 全限定触发或回复某条消息发送；支持百炼联网搜索
- `/profile`：查看用户画像，见「用户画像」章节
- `/maf`：男娘指数测定（回复某人消息可测对方）
- `/douyin`：下载抖音视频（三级解析兜底策略链）
- `/video`：下载 YouTube / Instagram / 小红书 视频
- `/searchimg`：以图搜图（SauceNAO）
- `/pack`：回复一条贴纸消息，下载所在贴纸包并将静态 WebP 转 PNG、TGS 转 GIF、WebM 转 GIF 后打包为 ZIP
- `/avatars`：获取用户全部历史头像（/avatars 取自己的，回复消息取对方的）
- `/info`：查看信息（回复消息查用户，群内直发查群/频道简介）
- `/matchmaker_register`：回复某人并注册到姻缘用户池
- `/kfc`、`/chp`、`/pyq`、`/du`：整活文案
- `/inline`：打开 inline 输入演示

支持 `/cmd@botname` 形式命令。

### 消息

- 文本消息：Echo
- 白名单群消息记录：开启 `bot.telegram.profile.record-enabled` 后，仅 `record-group-ids` 群白名单内的消息经 `listener/filter` 过滤链落 `chat_message` 表

### 回调按钮（Callback Query）

- `PING:*`：测试按钮回调
- `CALLBACK_BILI:GM/RM`：联动 B 站每日放送
- `XP_*`：XP 图片切换
- `NOOP`：占位按钮（AI 生成中，点击仅提示）
- `PROFILE_AUTH:Y|N:<userId>`：画像白名单授权（见下）

### Inline Query

通过 Provider 机制返回多种结果：随机涩图、XP 图片、B 站时间表、KFC/彩虹屁/朋友圈/毒鸡汤、编程语言榜、豆瓣搜索、AI 对话、抖音解析、姻缘卡池、文章等。

### Chosen Inline Query

- 随机涩图/XP：选中后随机替换图片
- AI：选中后异步将占位消息替换为模型回答
- 抖音：选中后解析视频

## 用户画像

链路：`chat_message`（消息记录）→ `ProfileAnalysisService`（逐用户增量取消息，连同用户名/昵称/Telegram 简介一并交给 LLM 生成/合并 JSON 画像）→ `user_profile` 表 → `/profile` 渲染（HTML 引用块 + 样本统计 + token 页脚）。

- 简介（bio）与 `/info` 同口径走 `GetChat`，用户未与 bot 私聊过时取不到，属正常降级；身份仅作线索，论据仍以聊天记录为准

- **生成时机**：xxl-job 每晚跑批 `UserProfileAnalysisJobHandler` 增量滚动合并；库里无画像时 `/profile` 会现场生成
- **强制重生开关**：`bot.telegram.profile.regenerate-on-query`（默认 true，每次查询现场全量重新生成；画像格式稳定后置 false，改由跑批更新、查询直接读库）
- **白名单数据库化**：`profile_allow_user` 表（PENDING/APPROVED/DENIED）
  - admin（yml `bot.telegram.profile.admin-user-ids`）免白名单直接用 `/profile`，回复某条消息后可查看该消息发送者的画像；不回复消息时查看自己
  - 普通用户只有被 admin 确认（APPROVED）后才能使用 `/profile`，且始终只能查看自己的画像，即使回复了其他用户的消息也不会越权查看
  - 普通用户无权限发 `/profile` → 落 PENDING 并推送带 `✅ 授权 / ❌ 拒绝` 按钮的审批消息，仅 admin 点击生效；点击后消息编辑为结果并清除按钮
  - 审批消息超时自动清理后再次发 `/profile` 会重新推送审批消息（被拒后同理）
- **消息自动清理**：`auto-delete-enabled`（默认开）；画像产出消息 + 命令消息 `auto-delete-delay-seconds`（默认 120s）后删除，授权申请按钮消息 `approval-request-delete-seconds`（默认 30s）后删除

## 数据表

`schema-mysql.sql`（`spring.sql.init.mode=always` 启动自动建表）：

| 表 | 用途 |
|---|---|
| `bot_user` | Telegram 用户资料（ID/用户名/昵称/头像 file_id 与二进制） |
| `chat_message` | 白名单群消息记录（画像分析语料） |
| `user_profile` | LLM 生成的用户画像（summary 正文、增量游标、累计 token、模型名） |
| `profile_allow_user` | /profile 白名单申请与授权状态 |
| `chat_image` | 贴纸/图片去重库（file_unique_id 维度，含贴纸包信息） |

## 架构说明

### 分层

`src/main/java/com/github/anicmv/telegrambot`：

- `gateway/longpolling`：Long Polling 入口
- `dispatcher`：`UpdateDispatcher` 总路由 + 按 `UpdateType` 的责任链 Processor
- `handler/command | callback | inline | message`：命令系统（`BotCommandRegistry`）、回调系统（`CallbackActionRegistry`）、Inline/Chosen Inline、消息处理
- `messenger`：`Messenger`/`TextSpec`/`Replier` 抽象（信使：出站消息能力，隔离 Telegram SDK）
- `repository | mapper | entity`：MyBatis-Plus 持久层
- `service`：画像分析、AI 对话、抖音解析、头像、贴纸打包等领域服务
- `job`：xxl-job 任务（画像跑批）
- `config | constant | model | event | utils`：配置属性、常量、领域模型、观测事件、工具

### 设计模式

- 注解驱动注册：`@BotCommand` / `@BotCallback` / `@BotInline`，Registry 启动扫描、重复即失败
- Strategy + Factory：按 `UpdateType` 分发；Chain of Responsibility：每类更新内部处理链（`@Order` 排序、STOP 短路）
- Adapter Pattern：`Messenger` 隔离 Telegram SDK
- Observer Pattern：事件监听观测
- 前缀通配：`@BotCallback("XP_*")` 支持 action 前缀匹配

## 快速开始

### 1. 设置环境变量

```bash
export TELEGRAM_BOT_TOKEN="你的 bot token"
export TELEGRAM_BOT_USERNAME="你的 bot 用户名"
export TELEGRAM_CHANNEL_ID="-100xxxxxxxxxx"
export DEEPSEEK_API_KEY="LLM api-key"   # 或百炼 DASHSCOPE_API_KEY，按 profile 配置 base-url/model
```

### 2. 安装媒体转换依赖

`/pack` 会将静态 WebP 转为 PNG、TGS 转为 GIF、WebM 转为 GIF。Gradle 会引入 TwelveMonkeys 的 WebP ImageIO 插件；运行环境还必须在 `PATH` 中提供：

```bash
ffmpeg -version
lottie_to_gif.sh --help
```

`lottie_to_gif.sh` 来自 [ed-asriyan/lottie-converter](https://github.com/ed-asriyan/lottie-converter)，其 GIF 输出还依赖 `gifski` 和 `gunzip`。可通过 `bot.telegram.pack.lottie-converter-command`、`ffmpeg-command`、`conversion-timeout-seconds` 配置命令和超时。缺少任一外部工具时，对应贴纸会跳过，其余贴纸仍会继续打包。

### 3. 如果之前用过 webhook，先删除

```bash
curl -X POST "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/deleteWebhook"
```

### 4. 配置

复制 `application-example.yaml` 为对应 profile 配置：数据源、`bot.telegram.*`、`spring.ai.*`。画像功能需配置 MySQL（自动建表）、`profile.record-enabled`、`profile.record-group-ids`、`profile.admin-user-ids`（务必配置，否则无人能审批）。

### 5. 启动

```bash
./gradlew bootRun
```

项目已在 Gradle `bootRun` 任务中固化 `--enable-native-access=ALL-UNNAMED`，避免 JDK 25 + Netty 的 native access 警告。

### 6. 基础验证

1. 给 Bot 发 `/start`，点 `Ping` 按钮验证回调
2. 发 `/kfc`、`/maf`、`/info` 等验证命令；输入 `@你的bot用户名` 测试 inline
3. 画像：把测试群配入 `record-group-ids` 并开启 `record-enabled`，聊几句后发 `/profile` → admin 收到审批消息 → 点 ✅ → 重新发 `/profile` 出画像
4. 发 `/douyin`、`/video`、`/searchimg` 验证解析下载链路

## 本地测试

```bash
./gradlew test
./gradlew build
./gradlew bootJar
```

测试中已关闭 `telegrambots.enabled`，避免单测阶段直接访问 Telegram 外网。

## 回调协议约定

统一使用 `ACTION:payload`，`:` 分割，第一段为注册 action：

- `PING:hello`
- `CALLBACK_BILI:GM` / `CALLBACK_BILI:RM`
- `XP_BS`、`XP_JK`、`XP_HS`、`XP_DEFAULT`（前缀通配注册 `XP_*`）
- `PROFILE_AUTH:Y:<userId>` / `PROFILE_AUTH:N:<userId>`

注意 Telegram 限制 callback_data ≤ 64 字节。

## 扩展指南

### 新增命令

1. 新建 `@Component` 类实现 `BotCommandHandler`
2. 标注 `@BotCommand(value = BotConstant.CMD_XXX, description = "...")`，自动注册进 `/help`
3. 群聊需 @ 限定触发的加 `groupRequireMention = true`

### 新增回调动作

1. 新建 `@Component` 类实现 `CallbackActionHandler`
2. 标注 `@BotCallback("ACTION")`（可前缀通配，如 `ORDER_*`）
3. 在 `execute(context, payload)` 处理业务，收尾务必 `messenger.answerCallback(...)` 消除客户端转圈

### 新增 Inline 结果

1. 新建类实现 `InlineQueryResultProvider`，`@BotInline("N_x")` 提供唯一 id
2. 需处理选中事件时配套 `ChosenInlineQueryHandler`

### 新增持久化

`entity`（`@TableName` + `@TableId(AUTO)` + 全列 `@TableField`）→ `mapper`（`extends BaseMapper`，`@MapperScan` 已覆盖）→ `repository`（`@Repository` 类 + `LambdaQueryWrapper`，upsert 注意撞唯一键重查转 update）。

### 新增多平台视频下载 / 以图搜图

分别在 `VideoDownloadService` 增加 Platform 枚举与检测正则、在 `SauceNaoService` 扩展解析逻辑。

### 新增 Update 类型

在 `UpdateType` 增加枚举 → `BotContext.from` 增加映射 → 增加对应 Processor 与 `UpdateHandler`。

## 常量/工具建议

- 单类常量：类内 `private static final`
- 跨类常量：统一收敛在 [BotConstant.java](src/main/java/com/github/anicmv/telegrambot/constant/BotConstant.java)
- `BotConstant` 建议分组：命令、回调、inline-id、HTTP 头、第三方 API
- 可变配置：`application-*.yaml + BotProperties`（`@ConfigurationProperties`）
- 真正无状态静态能力放 `utils`

## 注意事项

- 不要把真实 bot token 提交到仓库（`application-pro.yaml` 已 gitignore）
- 第三方 API（KFC、摸鱼图、B 站等）有失败或限流风险，生产建议加重试和降级
- LLM 画像分析消耗 token，跑批并发注意模型限流 QPM（`profile.analysis-concurrency`）
- 生产建议补充：限流、幂等、会话状态持久化、审计日志
