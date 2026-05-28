package com.mindecho.module.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建订单请求
 */
@Data
public class CreateOrderRequest {

    /** 会员类型：monthly / quarterly / yearly */
    @NotBlank(message = "会员类型不能为空")
    private String vipType;
}

