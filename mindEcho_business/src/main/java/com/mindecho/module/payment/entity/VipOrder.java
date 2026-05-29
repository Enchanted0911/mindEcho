package com.mindecho.module.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 会员订单实体
 */
@Data
@TableName("vip_order")
public class VipOrder {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 用户ID */
    @TableField("user_id")
    private String userId;

    /** 订单号 */
    @TableField("order_no")
    private String orderNo;

    /** 金额 */
    @TableField("amount")
    private BigDecimal amount;

    /** 订单状态：pending / paid / cancelled / refunded */
    @TableField("status")
    private String status;

    /** 会员类型：monthly / quarterly / yearly */
    @TableField("vip_type")
    private String vipType;

    /** VIP 到期时间（带时区） */
    @TableField("expire_time")
    private OffsetDateTime expireTime;

    /** 微信支付 prepay_id */
    @TableField("prepay_id")
    private String prepayId;

    /** 微信支付 transaction_id */
    @TableField("transaction_id")
    private String transactionId;

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

