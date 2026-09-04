package com.github.anicmv.telegrambot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * @author anicmv
 * @date 2026/3/22 18:00
 * @description bot_user 表实体。
 */
@Data
@TableName("bot_user")
public class BotUserEntity {

    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    @TableField("username")
    private String username;

    @TableField("nickname")
    private String nickname;

    @TableField("telegram_id")
    private Long telegramId;

    @TableField("avatar_file_id")
    private String avatarFileId;

    @TableField("avatar_data")
    private byte[] avatarData;

    @TableField("matchmaker_enabled")
    private Boolean matchmakerEnabled;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
