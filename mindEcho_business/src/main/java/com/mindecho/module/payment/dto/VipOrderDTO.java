package com.mindecho.module.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 会员订单 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VipOrderDTO {

    private String id;
    private String orderNo;
    private BigDecimal amount;
    private String status;
    private String vipType;
    /** VIP 到期时间（ISO-8601 带时区） */
    private OffsetDateTime expireTime;
    /** 创建时间（ISO-8601 带时区） */
    private OffsetDateTime createdTime;

    /** 微信支付参数（下单时返回） */
    private WxPayParams wxPayParams;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WxPayParams {
        private String timeStamp;
        private String nonceStr;
        private String pkg;
        private String signType;
        private String paySign;
    }
}

