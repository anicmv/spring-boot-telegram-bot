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
 * @description chat_message 表实体，记录白名单群内的原始消息。
 */
@Data
@TableName("chat_message")
public class ChatMessageEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("chat_id")
    private Long chatId;

    @TableField("telegram_user_id")
    private Long telegramUserId;

    @TableField("username")
    private String username;

    @TableField("nickname")
    private String nickname;

    @TableField("message_type")
    private String messageType;

    @TableField("content")
    private String content;

    @TableField("telegram_message_id")
    private Long telegramMessageId;

    @TableField("sent_at")
    private LocalDateTime sentAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
