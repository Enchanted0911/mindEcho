package com.mindecho.module.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 聊天会话实体
 */
@Data
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 用户ID */
    @TableField("user_id")
    private String userId;

    /** 会话标题 */
    @TableField("title")
    private String title;

    /** 使用的 AI 人格 */
    @TableField("ai_personality")
    private String aiPersonality;

    /** 逻辑删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间（带时区） */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private OffsetDateTime createdTime;

    /** 更新时间（带时区） */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedTime;
}

