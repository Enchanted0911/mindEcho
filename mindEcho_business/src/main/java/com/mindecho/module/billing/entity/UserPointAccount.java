package com.mindecho.module.billing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户积分账户实体
 *
 * <p>记录用户的可用积分、冻结积分及累计统计数据。
 * 冻结积分（frozen_balance）用于预扣场景：发起 AI 请求时冻结，
 * 结算后将实际消费从冻结中扣除，多余部分退回至可用余额。
 */
@Data
@TableName("user_point_account")
public class UserPointAccount {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 用户ID（唯一） */
    @TableField("user_id")
    private String userId;

    /** 可用积分余额（分）*/
    @TableField("balance")
    private Long balance;

    /** 冻结积分（预扣中，待结算） */
    @TableField("frozen_balance")
    private Long frozenBalance;

    /** 累计充值积分 */
    @TableField("total_recharge")
    private Long totalRecharge;

    /** 累计消费积分 */
    @TableField("total_consume")
    private Long totalConsume;

    /** 累计退回积分 */
    @TableField("total_refund")
    private Long totalRefund;

    /** 累计系统赠送积分 */
    @TableField("total_gift")
    private Long totalGift;

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

    /**
     * 是否余额足够
     */
    public boolean hasSufficientBalance(long required) {
        return balance != null && balance >= required;
    }
}

