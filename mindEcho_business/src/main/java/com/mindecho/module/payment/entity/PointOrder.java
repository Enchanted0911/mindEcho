package com.mindecho.module.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 积分充值订单实体
 *
 * <p>用户通过充值积分包获得积分，1 RMB = 100 积分。
 * 支付成功后调用 PointAccountService#recharge 增加积分余额。
 */
@Data
@TableName("point_order")
public class PointOrder {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 用户ID */
    @TableField("user_id")
    private String userId;

    /** 订单号 */
    @TableField("order_no")
    private String orderNo;

    /** 支付金额（元） */
    @TableField("amount")
    private BigDecimal amount;

    /** 充值积分数（= amount * 100） */
    @TableField("points")
    private Long points;

    /** 积分套餐类型（small/medium/large/extra_large 等） */
    @TableField("package_type")
    private String packageType;

    /**
     * 订单状态：pending / paid / cancelled / refunded
     */
    @TableField("status")
    private String status;

    /** 支付方式：wechat / alipay */
    @TableField("pay_channel")
    private String payChannel;

    /** 微信支付 prepay_id */
    @TableField("prepay_id")
    private String prepayId;

    /** 支付流水号（transaction_id） */
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

