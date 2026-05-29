package com.mindecho.module.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信支付 V3 配置属性
 *
 * <p>对应 application.yml 中的 wechat.pay.* 配置。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wechat.pay")
public class WxPayConfig {

    /**
     * 商户号（mchid）
     */
    private String mchId;

    /**
     * 商户 API V3 密钥（32位字符串，用于回调报文 AES-256-GCM 解密）
     */
    private String apiV3Key;

    /**
     * 商户 API 证书序列号（Serial Number）
     */
    private String certSerialNo;

    /**
     * 商户 API 私钥内容（PEM 格式，去掉 -----BEGIN/END PRIVATE KEY----- 头尾，合并为单行）
     */
    private String privateKey;

    /**
     * 微信支付平台证书内容（PEM 格式，用于验证回调签名）
     */
    private String platformCert;

    /**
     * 支付成功回调地址（需公网可访问且为 HTTPS）
     */
    private String notifyUrl;

    /**
     * 微信支付 V3 接口基础地址
     */
    private String baseUrl = "https://api.mch.weixin.qq.com";
}

