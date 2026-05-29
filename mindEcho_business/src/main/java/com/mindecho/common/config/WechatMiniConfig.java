package com.mindecho.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信小程序配置属性（对应 wechat.* 配置）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wechat")
public class WechatMiniConfig {

    /** 小程序 AppID */
    private String appid;

    /** 小程序 AppSecret */
    private String secret;

    /** 微信 code2session 接口地址 */
    private String loginUrl;
}

