package com.mindecho.common.result;

import lombok.Getter;

/**
 * 响应状态码枚举
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "success"),

    // 通用错误 1xxx
    PARAM_ERROR(1001, "参数错误"),
    PARAM_MISSING(1002, "参数缺失"),
    SYSTEM_ERROR(1500, "系统繁忙，请稍后重试"),

    // 认证错误 2xxx
    UNAUTHORIZED(2001, "请先登录"),
    TOKEN_EXPIRED(2002, "登录已过期，请重新登录"),
    TOKEN_INVALID(2003, "Token 无效"),
    WECHAT_LOGIN_FAILED(2004, "微信登录失败"),

    // 业务错误 3xxx
    USER_NOT_FOUND(3001, "用户不存在"),
    SESSION_NOT_FOUND(3002, "会话不存在"),
    MESSAGE_TOO_LONG(3003, "消息过长"),
    DAILY_LIMIT_EXCEEDED(3004, "今日免费消息次数已用完，请升级会员"),
    MESSAGE_NOT_FOUND(3005, "消息不存在"),
    DIARY_NOT_FOUND(3006, "日记不存在"),

    // 风险控制 4xxx
    HIGH_RISK_CONTENT(4001, "检测到高风险内容，已启动安全保护"),
    SENSITIVE_CONTENT(4002, "内容包含敏感词汇"),

    // 支付错误 5xxx
    PAYMENT_FAILED(5001, "支付失败"),
    ORDER_NOT_FOUND(5002, "订单不存在"),
    VIP_ALREADY_ACTIVE(5003, "会员权益已激活"),

    // 积分/计费错误 6xxx
    INSUFFICIENT_POINTS(6001, "积分不足，请充值"),
    POINT_ACCOUNT_NOT_FOUND(6002, "积分账户不存在"),
    POINT_DEDUCT_FAILED(6003, "积分扣除失败，请重试"),
    DUPLICATE_SETTLEMENT(6004, "重复结算，已忽略"),
    POINT_ORDER_NOT_FOUND(6005, "积分充值订单不存在"),

    // 占星错误 7xxx
    BIRTH_INFO_NOT_SET(7001, "请先设置出生信息"),
    SYNASTRY_PARTNER_NOT_SET(7002, "请先设置和盘对方信息"),
    SYNASTRY_CHART_NOT_FOUND(7003, "请先计算和盘后再进行解读"),
    TRANSIT_CHART_NOT_FOUND(7004, "请先计算流运后再进行解读"),
    NATAL_CHART_NOT_FOUND(7005, "请先计算本命盘后再进行解读");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}

