package com.mindecho.module.billing.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 积分账户信息 DTO（用于前端展示）
 */
@Data
@Builder
public class PointAccountDTO {

    /** 可用积分余额 */
    private Long balance;

    /** 冻结积分（预扣中，待结算） */
    private Long frozenBalance;

    /** 累计充值积分 */
    private Long totalRecharge;

    /** 累计消费积分 */
    private Long totalConsume;

    /** 累计退回积分 */
    private Long totalRefund;

    /** 累计赠送积分 */
    private Long totalGift;
}

