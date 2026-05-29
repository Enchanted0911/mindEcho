package com.mindecho.module.billing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水实体
 *
 * <p>记录每一次积分变动的详情，包括：充值、预扣、实际消费、退回、系统赠送、后台调整。
 * business_id 关联具体业务记录（如 ai_usage_record.id 或 vip_order.id）。
 */
@Data
@TableName("point_transaction")
public class PointTransaction {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 流水号（全局唯一，便于幂等校验） */
    @TableField("transaction_no")
    private String transactionNo;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /**
     * 变动积分（正数=增加，负数=减少）
     * 预扣时为负数（从 balance 扣）
     * 退回时为正数
     */
    @TableField("amount")
    private Long amount;

    /**
     * 流水类型：RECHARGE / PRE_DEDUCT / CONSUME / REFUND / SYSTEM_GIFT / ADMIN_ADJUST
     */
    @TableField("type")
    private String type;

    /** 变动前余额（可用积分） */
    @TableField("before_balance")
    private Long beforeBalance;

    /** 变动后余额（可用积分） */
    @TableField("after_balance")
    private Long afterBalance;

    /**
     * 关联业务ID
     * - RECHARGE/POINT_RECHARGE: vip_order.id 或 point_order.id
     * - PRE_DEDUCT/CONSUME/REFUND: ai_usage_record.id
     * - SYSTEM_GIFT/ADMIN_ADJUST: 后台操作 ID
     */
    @TableField("business_id")
    private Long businessId;

    /** 备注说明 */
    @TableField("remark")
    private String remark;

    /**
     * 状态: success / failed / reversed
     */
    @TableField("status")
    private String status;

    /** 创建时间 */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}

