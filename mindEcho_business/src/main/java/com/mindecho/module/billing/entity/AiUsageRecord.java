package com.mindecho.module.billing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * AI 使用量记录实体
 *
 * <p>记录每次 AI 调用的 token 消耗和积分计费详情。
 * 在发起预扣时创建（status=PRE_DEDUCTED），
 * 模型回复完成后更新实际用量和状态（status=SUCCESS）。
 */
@Data
@TableName("ai_usage_record")
public class AiUsageRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 请求唯一ID（幂等键，防止重复结算） */
    @TableField("request_id")
    private String requestId;

    /** 用户ID */
    @TableField("user_id")
    private String userId;

    /** 关联会话ID（chat_session.id，非聊天业务可为空） */
    @TableField("session_id")
    private String sessionId;

    /**
     * 业务类型：CHAT / ASTROLOGY_NATAL / ASTROLOGY_SYNASTRY / ASTROLOGY_TRANSIT / EMOTION_ANALYZE
     */
    @TableField("business_type")
    private String businessType;

    /** 使用的模型名称 */
    @TableField("model_name")
    private String modelName;

    /** 输入 token 数（prompt tokens） */
    @TableField("prompt_tokens")
    private Integer promptTokens;

    /** 输出 token 数（completion tokens） */
    @TableField("completion_tokens")
    private Integer completionTokens;

    /** 总 token 数 */
    @TableField("total_tokens")
    private Integer totalTokens;

    /** 上下文 token 数（用于阶梯计费，含 system prompt + memory + history） */
    @TableField("context_tokens")
    private Integer contextTokens;

    /** 预估积分（预扣时计算） */
    @TableField("estimated_points")
    private Long estimatedPoints;

    /** 实际消费积分（结算时计算） */
    @TableField("actual_points")
    private Long actualPoints;

    /** 基础费用（积分） */
    @TableField("base_points")
    private Long basePoints;

    /** 上下文阶梯费用（积分） */
    @TableField("context_points")
    private Long contextPoints;

    /** 模型倍率（*100 整数存储，如 100=1x, 150=1.5x, 300=3x） */
    @TableField("model_multiplier")
    private Integer modelMultiplier;

    /**
     * 状态
     * PRE_DEDUCTED: 已预扣
     * PROCESSING: AI处理中
     * SUCCESS: 成功结算
     * FAILED: 失败（全额退回）
     * REFUNDED: 已退款
     */
    @TableField("status")
    private String status;

    /** 是否由 streaming 中断（影响计费方式） */
    @TableField("streaming_interrupted")
    private Boolean streamingInterrupted;

    /** 创建时间（带时区） */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private OffsetDateTime createdTime;

    /** 更新时间（带时区） */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedTime;
}

