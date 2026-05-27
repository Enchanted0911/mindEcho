package com.mindecho.module.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员订单 DTO
 */
@Data
@Builder
public class VipOrderDTO {

    private Long id;
    private String orderNo;
    private BigDecimal amount;
    private String status;
    private String vipType;
    private LocalDateTime expireTime;
    private LocalDateTime createdTime;

    /** 微信支付参数（下单时返回） */
    private WxPayParams wxPayParams;

    @Data
    @Builder
    public static class WxPayParams {
        private String timeStamp;
        private String nonceStr;
        private String pkg;
        private String signType;
        private String paySign;
    }
}

