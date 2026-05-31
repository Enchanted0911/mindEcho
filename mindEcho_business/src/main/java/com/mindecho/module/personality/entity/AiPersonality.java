package com.mindecho.module.personality.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * AI 人格配置实体
 */
@Data
@TableName("ai_personality")
public class AiPersonality {

    @TableId(type = IdType.AUTO)
    private UUID id;

    /** 人格编码，唯一标识，对应 user.ai_personality 字段 */
    @TableField("code")
    private String code;

    /** 角色名（如：小柔、阿暖） */
    @TableField("name")
    private String name;

    /** 性别：female / male */
    @TableField("gender")
    private String gender;

    /** 风格类型：gentle / rational / snarky / midnight */
    @TableField("style")
    private String style;

    /** 展示 emoji */
    @TableField("emoji")
    private String emoji;

    /** 简短描述（前端展示） */
    @TableField("description")
    private String description;

    /** AI System Prompt 内容 */
    @TableField("system_prompt")
    private String systemPrompt;

    /** 排序权重，越小越靠前 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 是否启用 */
    @TableField("enabled")
    private Integer enabled;

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

