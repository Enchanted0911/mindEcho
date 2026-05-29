package com.mindecho.module.billing.enums;

import lombok.Getter;

/**
 * 积分流水类型枚举
 */
@Getter
public enum TransactionTypeEnum {

    /** 充值（购买积分包或VIP赠送积分） */
    RECHARGE("RECHARGE", "充值"),

    /** 预扣（AI请求发起时冻结） */
    PRE_DEDUCT("PRE_DEDUCT", "预扣"),

    /** 实际消费（最终结算扣除） */
    CONSUME("CONSUME", "消费"),

    /** 退回（预扣多余部分或失败全额退回） */
    REFUND("REFUND", "退回"),

    /** 系统赠送（新用户礼包、活动奖励等） */
    SYSTEM_GIFT("SYSTEM_GIFT", "系统赠送"),

    /** 后台调整（人工补单或修正） */
    ADMIN_ADJUST("ADMIN_ADJUST", "后台调整");

    private final String code;
    private final String desc;

    TransactionTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static TransactionTypeEnum ofCode(String code) {
        for (TransactionTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        throw new IllegalArgumentException("Unknown transaction type: " + code);
    }
}

