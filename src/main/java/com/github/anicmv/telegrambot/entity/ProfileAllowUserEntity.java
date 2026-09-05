package com.github.anicmv.telegrambot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * @author anicmv
 * @date 2026/9/5
 * @description profile_allow_user 表实体，/profile 命令白名单申请与授权记录。
 */
@Data
@TableName("profile_allow_user")
public class ProfileAllowUserEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("telegram_user_id")
    private Long telegramUserId;

    @TableField("status")
    private String status;

    @TableField("granted_by")
    private Long grantedBy;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
