package com.mindecho.module.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 积分充值订单 DTO
 */
@Data
@Builder
public class PointOrderDTO {

    private UUID id;
    private String orderNo;
    private BigDecimal amount;
    private Long points;
    private String packageType;
    private String status;
    /** 创建时间（ISO-8601 带时区） */
    private OffsetDateTime createdTime;

    /** 微信支付唤起参数（前端调用 uni.requestPayment 使用） */
    private VipOrderDTO.WxPayParams wxPayParams;
}

