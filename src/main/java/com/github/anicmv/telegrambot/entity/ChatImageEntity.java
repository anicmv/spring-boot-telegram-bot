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
 * @description chat_image 表实体，群聊静态图片库（sticker/photo），按 file_unique_id 去重。
 */
@Data
@TableName("chat_image")
public class ChatImageEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("image_type")
    private String imageType;

    @TableField("file_unique_id")
    private String fileUniqueId;

    @TableField("chat_id")
    private Long chatId;

    @TableField("telegram_user_id")
    private Long telegramUserId;

    @TableField("telegram_message_id")
    private Long telegramMessageId;

    @TableField("emoji")
    private String emoji;

    @TableField("set_name")
    private String setName;

    @TableField("width")
    private Integer width;

    @TableField("height")
    private Integer height;

    @TableField("image_data")
    private byte[] imageData;

    @TableField("file_size")
    private Integer fileSize;

    @TableField("sent_at")
    private LocalDateTime sentAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
