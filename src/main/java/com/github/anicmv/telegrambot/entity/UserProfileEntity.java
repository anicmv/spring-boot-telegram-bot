package com.github.anicmv.telegrambot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * @author anicmv
 * @date 2026/9/4
 * @description user_profile 表实体，存放大模型生成的用户画像。
 */
@Data
@TableName("user_profile")
public class UserProfileEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("telegram_user_id")
    private Long telegramUserId;

    @TableField("summary")
    private String summary;

    @TableField("report")
    private String report;

    @TableField("interests")
    private String interests;

    @TableField("personality")
    private String personality;

    @TableField("active_hours")
    private String activeHours;

    @TableField("frequent_topics")
    private String frequentTopics;

    @TableField("analyzed_message_count")
    private Integer analyzedMessageCount;

    @TableField("last_analyzed_message_id")
    private Long lastAnalyzedMessageId;

    @TableField("total_tokens")
    private Long totalTokens;

    @TableField("model")
    private String model;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
