package com.mindecho.module.memory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 长期记忆实体
 */
@Data
@TableName("memory")
public class Memory {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 用户ID */
    @TableField("user_id")
    private String userId;

    /** 记忆类型：profile（用户画像） / event（事件） / emotion（情绪特征） */
    @TableField("memory_type")
    private String memoryType;

    /** 记忆内容 */
    @TableField("content")
    private String content;

    /** 重要度评分 1-10 */
    @TableField("importance_score")
    private Integer importanceScore;

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

