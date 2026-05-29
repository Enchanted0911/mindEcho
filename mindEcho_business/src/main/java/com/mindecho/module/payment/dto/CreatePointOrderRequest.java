package com.mindecho.module.payment.dto;

import lombok.Data;

/**
 * 创建积分充值订单请求
 */
@Data
public class CreatePointOrderRequest {

    /**
     * 积分套餐类型
     * small: 6元100积分
     * medium: 30元500积分（赠50）
     * large: 98元1000积分（赠200）
     * extra_large: 198元2500积分（赠500）
     */
    private String packageType;
}

