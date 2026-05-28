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
    VIP_ALREADY_ACTIVE(5003, "会员权益已激活");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}

