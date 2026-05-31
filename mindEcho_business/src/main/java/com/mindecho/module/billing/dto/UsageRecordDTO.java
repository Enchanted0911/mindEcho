package com.mindecho.module.billing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * AI 使用量记录 DTO（用于消费详情展示）
 */
@Data
@Builder
public class UsageRecordDTO {

    private UUID id;

    /** 业务类型（CHAT/ASTROLOGY_NATAL 等） */
    private String businessType;

    /** 业务类型描述 */
    private String businessTypeDesc;

    /** 使用的模型名称 */
    private String modelName;

    /** 输入 token 数 */
    private Integer promptTokens;

    /** 输出 token 数 */
    private Integer completionTokens;

    /** 总 token 数 */
    private Integer totalTokens;

    /** 上下文 token 数 */
    private Integer contextTokens;

    /** 预估积分（预扣时） */
    private Long estimatedPoints;

    /** 实际消费积分 */
    private Long actualPoints;

    /** 状态 */
    private String status;

    /** 创建时间（ISO-8601 带时区） */
    private OffsetDateTime createdTime;
}

