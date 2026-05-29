package com.mindecho.module.billing.enums;

import lombok.Getter;

/**
 * AI 使用记录状态枚举
 */
@Getter
public enum UsageStatusEnum {

    /** 已预扣，等待AI处理 */
    PRE_DEDUCTED("PRE_DEDUCTED", "已预扣"),

    /** AI处理中（streaming） */
    PROCESSING("PROCESSING", "处理中"),

    /** 成功结算 */
    SUCCESS("SUCCESS", "成功"),

    /** 失败（全额退回预扣积分） */
    FAILED("FAILED", "失败"),

    /** 已退款 */
    REFUNDED("REFUNDED", "已退款");

    private final String code;
    private final String desc;

    UsageStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }
}

