CREATE TABLE IF NOT EXISTS bot_user (
    user_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(128) DEFAULT NULL COMMENT 'Telegram username',
    nickname VARCHAR(255) DEFAULT NULL COMMENT 'Telegram 昵称',
    telegram_id BIGINT NOT NULL COMMENT 'Telegram 用户 ID',
    avatar_file_id VARCHAR(512) DEFAULT NULL COMMENT 'Telegram 头像 file_id',
    avatar_data LONGBLOB DEFAULT NULL COMMENT 'Telegram 头像二进制数据',
    matchmaker_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否参与红娘系统随机',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_bot_user_telegram_id (telegram_id),
    KEY idx_bot_user_matchmaker_enabled (matchmaker_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Bot 用户资料表';

CREATE TABLE IF NOT EXISTS chat_message (
    id                  BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    chat_id             BIGINT NOT NULL COMMENT '群 ID',
    telegram_user_id    BIGINT NOT NULL COMMENT '发言者 Telegram ID',
    username            VARCHAR(128) DEFAULT NULL COMMENT '发言者 username',
    nickname            VARCHAR(255) DEFAULT NULL COMMENT '发言者昵称',
    message_type        VARCHAR(32) NOT NULL COMMENT '消息类型：text/photo/sticker/video/voice',
    content             TEXT DEFAULT NULL COMMENT '文本内容，截断至 2000 字符',
    telegram_message_id BIGINT NOT NULL COMMENT 'Telegram 消息 ID',
    sent_at             TIMESTAMP NOT NULL COMMENT '消息发送时间',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_message (chat_id, telegram_message_id),
    KEY idx_chat_message_user_id (telegram_user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群聊消息记录表';

CREATE TABLE IF NOT EXISTS user_profile (
    id                       BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    telegram_user_id         BIGINT NOT NULL COMMENT 'Telegram 用户 ID',
    summary                  TEXT DEFAULT NULL COMMENT '自由文本画像摘要',
    interests                JSON DEFAULT NULL COMMENT '兴趣标签数组',
    personality              JSON DEFAULT NULL COMMENT '性格特质',
    active_hours             VARCHAR(128) DEFAULT NULL COMMENT '活跃时段',
    frequent_topics          JSON DEFAULT NULL COMMENT '高频话题',
    analyzed_message_count   INT NOT NULL DEFAULT 0 COMMENT '累计已分析消息数',
    last_analyzed_message_id BIGINT NOT NULL DEFAULT 0 COMMENT '增量游标：最后分析到的 chat_message.id',
    model                    VARCHAR(64) DEFAULT NULL COMMENT '生成画像所用模型',
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_profile_telegram_id (telegram_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户画像表';

CREATE TABLE IF NOT EXISTS chat_image (
    id                  BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    image_type          VARCHAR(16) NOT NULL COMMENT '图片类型：sticker/photo',
    file_unique_id      VARCHAR(128) NOT NULL COMMENT 'Telegram file_unique_id，去重键：同一图片跨群只存一次',
    chat_id             BIGINT NOT NULL COMMENT '首次发送群 ID',
    telegram_user_id    BIGINT NOT NULL COMMENT '首次发送者 Telegram ID',
    telegram_message_id BIGINT NOT NULL COMMENT '首次发送消息 ID',
    emoji               VARCHAR(64) DEFAULT NULL COMMENT '贴纸表情（仅 sticker）',
    set_name            VARCHAR(128) DEFAULT NULL COMMENT '贴纸包名（仅 sticker）',
    width               INT DEFAULT NULL COMMENT '图片宽',
    height              INT DEFAULT NULL COMMENT '图片高',
    image_data          LONGBLOB DEFAULT NULL COMMENT '图片二进制数据（webp/jpg）',
    file_size           INT DEFAULT NULL COMMENT '字节数',
    sent_at             TIMESTAMP NOT NULL COMMENT '消息发送时间',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_image_file (file_unique_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群聊静态图片库';
