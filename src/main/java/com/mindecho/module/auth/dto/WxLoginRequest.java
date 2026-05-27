package com.mindecho.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信登录请求
 */
@Data
public class WxLoginRequest {

    @NotBlank(message = "code 不能为空")
    private String code;
}

