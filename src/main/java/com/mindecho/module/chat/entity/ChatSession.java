package com.mindecho.module.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天会话实体
 */
@Data
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 会话标题 */
    @TableField("title")
    private String title;

    /** 使用的 AI 人格 */
    @TableField("personality")
    private String personality;

    /** 逻辑删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间 */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}

