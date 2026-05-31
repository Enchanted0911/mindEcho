package com.mindecho.module.memory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 长期记忆实体（结构化存储）
 *
 * <p>存储类型（memory_type）：
 * <ul>
 *   <li>FACT         — 事实记忆（我叫Johnson / 我是程序员）→ 长期记忆</li>
 *   <li>PREFERENCE   — 偏好记忆（喜欢咖啡 / 喜欢猫）→ 长期记忆</li>
 *   <li>GOAL         — 目标记忆（我要考雅思 / 我要转AI）→ 长期记忆</li>
 *   <li>RELATIONSHIP — 关系记忆（我老婆叫Lisa）→ 长期记忆</li>
 *   <li>PROFILE      — 综合画像（兼容旧数据）→ 长期记忆</li>
 *   <li>SUMMARY      — 记忆摘要（压缩后生成）→ 长期记忆</li>
 * </ul>
 */
@Data
@TableName("memory")
public class Memory {

    @TableId(type = IdType.AUTO)
    private UUID id;

    /** 用户ID */
    @TableField("user_id")
    private UUID userId;

    /** 记忆类型：FACT / PREFERENCE / GOAL / RELATIONSHIP / PROFILE / SUMMARY */
    @TableField("memory_type")
    private String memoryType;

    /** 记忆内容 */
    @TableField("content")
    private String content;

    /** 重要度评分 0-10 */
    @TableField("importance_score")
    private Integer importanceScore;

    /** 召回次数（用于 memoryScore 频率因子） */
    @TableField("recall_count")
    private Long recallCount;

    /** 最近召回时间（用于 memoryScore 时效因子） */
    @TableField("last_recalled_time")
    private OffsetDateTime lastRecalledTime;

    /**
     * 记忆健康分（0~1）
     * memoryScore = 0.5 × importance_norm + 0.3 × frequency_norm + 0.2 × recency_norm
     * 由每日定时任务计算，低于阈值时触发遗忘压缩
     */
    @TableField("memory_score")
    private Double memoryScore;

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

