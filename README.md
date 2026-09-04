# spring-boot-telegram-bot

基于 `Spring Boot 4.1.1 + JDK 25 + TelegramBots 10.2.1` 的 Telegram 机器人项目，当前使用 **Long Polling** 模式。

项目目标：从一开始就支持可扩展的文本、命令、图片、按钮回调、Inline 查询、Chosen Inline Query。

## 技术栈

- Spring Boot `4.1.1`
- Java `25`
- Telegram Bots `10.2.1`
- WebClient（`spring-boot-starter-webclient`）
- Jsoup（用于编程语言榜抓取）
- yt-dlp（YouTube / Instagram / 小红书 视频下载）
- Gradle（Groovy DSL）

## 已实现能力

### 命令

- `/start`：发送欢迎文本 + `Ping` 按钮
- `/help`：帮助说明
- `/ping`：返回 `🏓`
- `/kfc`：获取疯狂星期四文案
- `/chp`：获取彩虹屁
- `/pyq`：获取朋友圈文案
- `/du`：获取毒鸡汤
- `/moyu`：发送摸鱼图片
- `/maf`：男娘指数测定
- `/douyin`：下载抖音视频
- `/video`：下载 YouTube / Instagram / 小红书 视频
- `/searchimg`：以图搜图（ SauceNAO ）

支持 `/cmd@botname` 形式命令。

### 消息

- 文本消息：Echo
- 图片消息：占位处理（可继续接 OCR/审核等）

### 回调按钮（Callback Query）

- `PING:*`：测试按钮回调
- `CALLBACK_BILI:GM/RM`：联动 B 站每日放送
- `XP_*`：XP 图片切换

### Inline Query

通过 Provider 机制返回多种结果：

- `RandomEcyImageQueryResultProvider`
- `XpQueryResultProvider`
- `BiliTimelineResultProvider`
- `KfcInlineQueryResultProvider`
- `ChpInlineQueryResultProvider`
- `PyqInlineQueryResultProvider`
- `DuInlineQueryResultProvider`
- `TopProgrammingLanguagesResultProvider`
- `ArticleInlineQueryResultProvider`

### Chosen Inline Query

- `RandomEcyImageChosenInlineQueryHandler`：选中内联结果后随机替换图片

## 架构说明

### 分层

- `adapter`：接入层（long polling）
- `application`：路由、策略、责任链、命令/回调/inline 处理
- `domain`：核心模型与网关抽象
- `infrastructure`：配置、Telegram SDK 适配、观测
- `common`：跨层工具类

### 设计模式

- Strategy + Factory：按 `UpdateType` 分发
- Chain of Responsibility：每类更新内部处理链
- Command Pattern：命令注册与执行
- Adapter Pattern：`Messenger` 隔离 Telegram SDK
- Observer Pattern：事件监听观测

## 关键目录

`src/main/java/com/github/anicmv/bot`

- `adapter/longpolling`：Long Polling 入口
- `application/dispatch`：总路由 + 策略
- `application/handler/command`：命令系统
- `application/handler/callback`：回调系统
- `application/handler/inline`：Inline/Chosen Inline 处理
- `application/handler/inline/provider`：Inline 结果提供器机制
- `domain/messenger`：`Messenger` 抽象（信使：出站消息能力）
- `infrastructure/telegram`：Telegram 实现
- `common/constant`：`BotConstant` 全局常量
- `common/util`：`HttpUtil`、`BotUtil`

## 快速开始

### 1. 设置环境变量

```bash
export TELEGRAM_BOT_TOKEN="你的 bot token"
export TELEGRAM_BOT_USERNAME="你的 bot 用户名"
export TELEGRAM_CHANNEL_ID="-100xxxxxxxxxx"
```

### 2. 如果之前用过 webhook，先删除

```bash
curl -X POST "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/deleteWebhook"
```

### 3. 启动

```bash
./gradlew bootRun
```

项目已在 Gradle `bootRun` 任务中固化：

- `--enable-native-access=ALL-UNNAMED`

用于避免 JDK 25 + Netty 的 native access 警告。

### 4. 基础验证

1. 给 Bot 发 `/start`
2. 点 `Ping` 按钮，应收到回调反馈
3. 发 `/kfc`、`/chp`、`/pyq`、`/du`、`/moyu`、`/maf` 验证命令
4. 在输入框使用 `@你的bot用户名` 测试 inline 结果
5. 点击 B 站 inline 结果里的 `国漫/日漫` 按钮，验证回调联动
6. 发 `/video https://www.youtube.com/watch?v=...` 测试多平台视频下载
7. 发 `/searchimg https://...` 测试以图搜图

## 本地测试

```bash
./gradlew test
./gradlew build
./gradlew bootJar
```

测试中已关闭 `telegrambots.enabled`，避免单测阶段直接访问 Telegram 外网。

## 回调协议约定

统一使用：

- `ACTION:payload`

例如：

- `PING:hello`
- `CALLBACK_BILI:GM`
- `CALLBACK_BILI:RM`

XP 使用前缀动作：

- `XP_BS`、`XP_JK`、`XP_HS`、`XP_DEFAULT`

注册中心支持前缀匹配（如 `XP_*`）。

## 扩展指南

### 新增命令

1. 新建类实现 `BotCommandHandler`
2. 返回 `command()`
3. `@Component` 自动注册

### 新增回调动作

1. 新建类实现 `CallbackActionHandler`
2. 定义 `action()`（可前缀通配，如 `ORDER_*`）
3. 在 `execute` 处理业务

### 新增 Inline 结果

1. 新建类实现 `InlineQueryResultProvider`
2. 提供唯一 `sortId()`
3. 在 `createResult` 返回 `InlineQueryResult`

### 新增多平台视频下载

在 `VideoDownloadService` 中新增 Platform 枚举和检测正则即可，例如：

```java
private static final Pattern XXX_PATTERN = Pattern.compile("(?:xhs|...)\\S+", Pattern.CASE_INSENSITIVE);
```

### 新增以图搜图来源

在 `SauceNaoService` 中扩展解析逻辑即可。

### 新增 Update 类型

1. 在 `UpdateType` 增加枚举
2. 在 `BotContext.from` 增加映射
3. 增加对应 `Strategy`
4. 添加 `UpdateHandler`

## 常量/工具建议

- 单类常量：类内 `private static final`
- 跨类常量：统一收敛在 [BotConstant.java](src/main/java/com/github/anicmv/bot/common/constant/BotConstant.java)
- `BotConstant` 建议分组：命令、回调、inline-id、HTTP 头、第三方 API
- 可变配置：`application-*.yaml + @ConfigurationProperties`
- 真正无状态静态能力放 `common/util`

## 注意事项

- 不要把真实 bot token 提交到仓库
- 第三方 API（如 KFC、摸鱼图、B 站）有失败或限流风险，生产建议加重试和降级
- 生产建议补充：限流、幂等、黑白名单、会话状态持久化、审计日志
