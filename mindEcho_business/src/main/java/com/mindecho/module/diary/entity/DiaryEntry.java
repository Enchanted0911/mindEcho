package com.mindecho.module.diary.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 情绪日记实体
 *
 * <p>diary_date 使用 LocalDate（只关心日期，不含时间），
 * 创建/更新时间使用 OffsetDateTime 存储带时区的精确时刻。
 */
@Data
@TableName("diary_entry")
public class DiaryEntry {

    @TableId(type = IdType.AUTO)
    private UUID id;

    /** 用户ID */
    @TableField("user_id")
    private UUID userId;

    /** 日记日期（纯日期，不含时区） */
    @TableField("diary_date")
    private LocalDate diaryDate;

    /** 情绪类型 */
    @TableField("emotion")
    private String emotion;

    /** 情绪强度 1-10 */
    @TableField("emotion_intensity")
    private Integer emotionIntensity;

    /** 日记内容 */
    @TableField("content")
    private String content;

    /** AI 总结 */
    @TableField("ai_summary")
    private String aiSummary;

    /** 天气 */
    @TableField("weather")
    private String weather;

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

