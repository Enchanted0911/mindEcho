package com.mindecho.module.billing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 积分流水记录 DTO
 */
@Data
@Builder
public class TransactionRecordDTO {

    private UUID id;

    /** 流水号 */
    private String transactionNo;

    /** 变动积分（正数=增加，负数=减少） */
    private Long amount;

    /** 流水类型（RECHARGE/PRE_DEDUCT/CONSUME/REFUND/SYSTEM_GIFT/ADMIN_ADJUST） */
    private String type;

    /** 流水类型描述 */
    private String typeDesc;

    /** 变动前余额 */
    private Long beforeBalance;

    /** 变动后余额 */
    private Long afterBalance;

    /** 备注 */
    private String remark;

    /** 状态 */
    private String status;

    /** 创建时间（ISO-8601 带时区） */
    private OffsetDateTime createdTime;
}

