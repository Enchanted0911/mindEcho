package com.mindecho.module.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分充值订单 DTO
 */
@Data
@Builder
public class PointOrderDTO {

    private Long id;
    private String orderNo;
    private BigDecimal amount;
    private Long points;
    private String packageType;
    private String status;
    private LocalDateTime createdTime;

    /** 微信支付唤起参数（前端调用 uni.requestPayment 使用） */
    private VipOrderDTO.WxPayParams wxPayParams;
}

