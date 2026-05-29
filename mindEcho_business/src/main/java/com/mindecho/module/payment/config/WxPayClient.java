package com.mindecho.module.payment.config;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * 微信支付 V3 HTTP 客户端
 *
 * <p>封装：
 * <ul>
 *   <li>请求签名（SHA256withRSA）</li>
 *   <li>回调签名验证</li>
 *   <li>回调报文 AES-256-GCM 解密</li>
 *   <li>小程序统一下单（JSAPI）</li>
 *   <li>构建前端唤起支付的签名参数</li>
 * </ul>
 *
 * <p>微信支付 V3 接口文档：https://pay.weixin.qq.com/wiki/doc/apiv3/index.shtml
 */
@Slf4j
@Component
public class WxPayClient {

    private static final String MEDIA_TYPE_JSON = "application/json; charset=utf-8";
    private static final String SCHEMA = "WECHATPAY2-SHA256-RSA2048";
    /** AES-GCM 认证标签长度（bit） */
    private static final int GCM_TAG_LENGTH = 128;

    private final WxPayConfig wxPayConfig;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    public WxPayClient(WxPayConfig wxPayConfig,
                       OkHttpClient okHttpClient,
                       @Qualifier("webObjectMapper") ObjectMapper objectMapper) {
        this.wxPayConfig = wxPayConfig;
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
    }

    // ─────────────────────── 统一下单（小程序 JSAPI） ───────────────────────

    /**
     * 调用微信支付 V3 统一下单接口（JSAPI - 小程序支付）
     *
     * @param appId   小程序 appId
     * @param openid  用户 openid
     * @param orderNo 商户订单号
     * @param amount  金额（元，精确到分）
     * @param desc    商品描述
     * @return prepay_id（用于构建前端唤起支付的签名参数）
     */
    public String unifiedOrderJsapi(String appId, String openid, String orderNo,
                                    BigDecimal amount, String desc) {
        String url = wxPayConfig.getBaseUrl() + "/v3/pay/transactions/jsapi";
        // 元 -> 分
        int totalFen = amount.multiply(new BigDecimal("100")).intValue();

        String body = buildUnifiedOrderBody(appId, openid, orderNo, totalFen, desc);
        log.info("WxPay unifiedOrder request: orderNo={}, amount={}fen", orderNo, totalFen);

        String responseBody = doPost(url, body);
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            if (json.has("prepay_id")) {
                String prepayId = json.get("prepay_id").asText();
                log.info("WxPay unifiedOrder success: orderNo={}, prepayId={}", orderNo, prepayId);
                return prepayId;
            }
            String errCode = json.has("code") ? json.get("code").asText() : "UNKNOWN";
            String errMsg = json.has("message") ? json.get("message").asText() : responseBody;
            log.error("WxPay unifiedOrder failed: code={}, message={}", errCode, errMsg);
            throw new BusinessException(ResultCode.PAYMENT_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("WxPay unifiedOrder parse error, response={}", responseBody, e);
            throw new BusinessException(ResultCode.PAYMENT_FAILED);
        }
    }

    /**
     * 根据 prepay_id 构建前端小程序唤起支付的签名参数
     *
     * @param appId    小程序 appId
     * @param prepayId 统一下单返回的 prepay_id
     * @return 前端所需签名参数
     */
    public WxPaySignParams buildMiniPayParams(String appId, String prepayId) {
        String timeStamp = String.valueOf(Instant.now().getEpochSecond());
        String nonceStr = IdUtil.fastSimpleUUID();
        String pkg = "prepay_id=" + prepayId;

        // 签名消息串：appId\n时间戳\nnonce\npackage\n
        String message = appId + "\n" + timeStamp + "\n" + nonceStr + "\n" + pkg + "\n";
        String paySign = sign(message);

        return WxPaySignParams.builder()
                .timeStamp(timeStamp)
                .nonceStr(nonceStr)
                .pkg(pkg)
                .signType("RSA")
                .paySign(paySign)
                .build();
    }

    // ─────────────────────── 回调验签 & 解密 ───────────────────────

    /**
     * 验证微信支付回调请求头签名
     *
     * <p>微信回调请求头：
     * <ul>
     *   <li>Wechatpay-Signature：Base64 编码签名值</li>
     *   <li>Wechatpay-Timestamp：时间戳（秒）</li>
     *   <li>Wechatpay-Nonce：随机串</li>
     * </ul>
     *
     * @param timestamp 请求头 Wechatpay-Timestamp
     * @param nonce     请求头 Wechatpay-Nonce
     * @param signature 请求头 Wechatpay-Signature
     * @param body      原始请求体（不可 buffered/decoded）
     * @return true=验签通过
     */
    public boolean verifyCallback(String timestamp, String nonce, String signature, String body) {
        try {
            // 1. 构造验签消息串：时间戳\nnonce\nbody\n
            String message = timestamp + "\n" + nonce + "\n" + body + "\n";

            // 2. 从平台证书中取公钥
            PublicKey publicKey = loadPlatformPublicKey();

            // 3. SHA256withRSA 验签
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));

            byte[] signBytes = Base64.getDecoder().decode(signature);
            return verifier.verify(signBytes);
        } catch (Exception e) {
            log.error("WxPay callback signature verification failed", e);
            return false;
        }
    }

    /**
     * 解密微信支付回调中 resource 字段的 ciphertext（AES-256-GCM）
     *
     * @param algorithm      加密算法，固定为 "AEAD_AES_256_GCM"
     * @param nonce          12字节 nonce（来自回调 resource.nonce）
     * @param associatedData 附加数据（来自回调 resource.associated_data，可为 null）
     * @param ciphertext     Base64 编码密文（来自回调 resource.ciphertext）
     * @return 解密后的业务 JSON 字符串（含 out_trade_no / transaction_id 等）
     */
    public String decryptCallback(String algorithm, String nonce,
                                  String associatedData, String ciphertext) {
        if (!"AEAD_AES_256_GCM".equals(algorithm)) {
            throw new BusinessException("不支持的加密算法: " + algorithm);
        }
        try {
            byte[] keyBytes = wxPayConfig.getApiV3Key().getBytes(StandardCharsets.UTF_8);
            byte[] cipherBytes = Base64.getDecoder().decode(ciphertext);
            byte[] nonceBytes = nonce.getBytes(StandardCharsets.UTF_8);
            byte[] aadBytes = (associatedData != null ? associatedData : "")
                    .getBytes(StandardCharsets.UTF_8);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonceBytes);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);
            cipher.updateAAD(aadBytes);

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("WxPay callback decryption failed", e);
            throw new BusinessException(ResultCode.PAYMENT_FAILED);
        }
    }

    // ─────────────────────── 签名 ───────────────────────

    /**
     * 使用商户 API 私钥对消息串做 SHA256withRSA 签名，返回 Base64 编码结果
     */
    private String sign(String message) {
        try {
            PrivateKey privateKey = loadMerchantPrivateKey();
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            log.error("WxPay sign error", e);
            throw new BusinessException(ResultCode.PAYMENT_FAILED);
        }
    }

    /**
     * 构造微信支付 V3 Authorization 请求头
     *
     * <p>格式：WECHATPAY2-SHA256-RSA2048 mchid="...",nonce_str="...",signature="...",timestamp="...",serial_no="..."
     */
    private String buildAuthorization(String nonce, String timestamp, String signature) {
        return SCHEMA + " " +
                "mchid=\"" + wxPayConfig.getMchId() + "\"," +
                "nonce_str=\"" + nonce + "\"," +
                "signature=\"" + signature + "\"," +
                "timestamp=\"" + timestamp + "\"," +
                "serial_no=\"" + wxPayConfig.getCertSerialNo() + "\"";
    }

    // ─────────────────────── HTTP ───────────────────────

    /**
     * 带微信支付 V3 认证头的 POST 请求
     */
    private String doPost(String url, String bodyStr) {
        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String nonce = IdUtil.fastSimpleUUID();

            // 签名消息串：HTTP方法\nURL路径\n时间戳\nnonce\nbody\n
            // 微信支付 V3 签名要求：URL 路径部分（含 query string，不含 scheme+host）
            String baseUrl = wxPayConfig.getBaseUrl();
            String urlPath = url.startsWith(baseUrl) ? url.substring(baseUrl.length()) : url;
            if (!urlPath.startsWith("/")) {
                urlPath = "/" + urlPath;
            }
            String signMessage = "POST\n" + urlPath + "\n" + timestamp + "\n" + nonce + "\n" + bodyStr + "\n";
            String signature = sign(signMessage);
            String authorization = buildAuthorization(nonce, timestamp, signature);

            RequestBody requestBody = RequestBody.create(bodyStr, MediaType.parse(MEDIA_TYPE_JSON));
            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Authorization", authorization)
                    .header("Accept", "application/json")
                    .header("Content-Type", MEDIA_TYPE_JSON)
                    .header("User-Agent", "MindEcho/1.0")
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("WxPay HTTP error: status={}, body={}", response.code(), respBody);
                }
                return respBody;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("WxPay HTTP request failed: url={}", url, e);
            throw new BusinessException(ResultCode.PAYMENT_FAILED);
        }
    }

    // ─────────────────────── 密钥加载 ───────────────────────

    /**
     * 从配置中加载商户 API 私钥（PKCS8 格式）
     */
    private PrivateKey loadMerchantPrivateKey() throws Exception {
        String keyStr = wxPayConfig.getPrivateKey()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("[\\r\\n\\s]+", "");
        byte[] keyBytes = Base64.getDecoder().decode(keyStr);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    /**
     * 从配置中加载微信支付平台证书公钥（X.509 PEM 格式）
     *
     * <p>使用标准 Java {@link CertificateFactory} 解析，无需第三方库。</p>
     */
    private PublicKey loadPlatformPublicKey() throws Exception {
        String certStr = wxPayConfig.getPlatformCert().trim();
        // 补全 PEM 头尾（若配置中已去掉则补回）
        if (!certStr.startsWith("-----")) {
            certStr = "-----BEGIN CERTIFICATE-----\n" + certStr + "\n-----END CERTIFICATE-----";
        }
        byte[] certBytes = certStr.getBytes(StandardCharsets.UTF_8);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(certBytes));
        return cert.getPublicKey();
    }

    // ─────────────────────── 请求体构建 ───────────────────────

    private String buildUnifiedOrderBody(String appId, String openid, String orderNo,
                                         int totalFen, String desc) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("appid", appId);
            root.put("mchid", wxPayConfig.getMchId());
            root.put("description", StringUtils.hasText(desc) ? desc : "心屿会员");
            root.put("out_trade_no", orderNo);
            root.put("notify_url", wxPayConfig.getNotifyUrl());

            ObjectNode amount = objectMapper.createObjectNode();
            amount.put("total", totalFen);
            amount.put("currency", "CNY");
            root.set("amount", amount);

            ObjectNode payer = objectMapper.createObjectNode();
            payer.put("openid", openid);
            root.set("payer", payer);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }

    // ─────────────────────── DTO ───────────────────────

    /**
     * 前端小程序唤起支付所需的签名参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WxPaySignParams {
        /** 时间戳（秒，字符串） */
        private String timeStamp;
        /** 随机串 */
        private String nonceStr;
        /** "prepay_id=xxx" */
        private String pkg;
        /** 签名类型，固定 "RSA" */
        private String signType;
        /** 支付签名 */
        private String paySign;
    }
}

