package com.mindecho.module.emotion.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 情绪记录实体
 */
@Data
@TableName("emotion_record")
public class EmotionRecord {

    @TableId(type = IdType.AUTO)
    private UUID id;

    /** 用户ID */
    @TableField("user_id")
    private UUID userId;

    /** 情绪类型 */
    @TableField("emotion")
    private String emotion;

    /** 风险等级 */
    @TableField("risk_level")
    private String riskLevel;

    /** 原始文本 */
    @TableField("source_text")
    private String sourceText;

    /** 逻辑删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间（带时区） */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private OffsetDateTime createdTime;
}

