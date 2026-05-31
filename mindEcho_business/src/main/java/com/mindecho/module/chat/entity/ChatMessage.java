package com.mindecho.module.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 聊天消息实体
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private UUID id;

    /** 会话ID */
    @TableField("session_id")
    private UUID sessionId;

    /** 用户ID */
    @TableField("user_id")
    private UUID userId;

    /** 角色：user / assistant */
    @TableField("role")
    private String role;

    /** 消息内容 */
    @TableField("content")
    private String content;

    /** Token 消耗数 */
    @TableField("token_count")
    private Integer tokenCount;

    /** 情绪标签 */
    @TableField("emotion")
    private String emotion;

    /** 风险等级 */
    @TableField("risk_level")
    private String riskLevel;

    /** 逻辑删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间（带时区） */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private OffsetDateTime createdTime;
}

