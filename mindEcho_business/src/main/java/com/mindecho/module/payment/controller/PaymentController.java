package com.mindecho.module.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindecho.common.result.Result;
import com.mindecho.common.util.UserContext;
import com.mindecho.module.payment.config.WxPayClient;
import com.mindecho.module.payment.dto.CreateOrderRequest;
import com.mindecho.module.payment.dto.CreatePointOrderRequest;
import com.mindecho.module.payment.dto.PointOrderDTO;
import com.mindecho.module.payment.dto.VipOrderDTO;
import com.mindecho.module.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 支付 Controller
 *
 * <p>接口列表：
 * <ul>
 *   <li>POST /api/payment/order         — 创建会员订单（需登录）</li>
 *   <li>GET  /api/payment/order/{orderNo}— 查询订单状态（需登录，前端轮询）</li>
 *   <li>POST /api/payment/wx-callback   — 微信支付成功回调（白名单，无需 Token）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentService paymentService;
    private final WxPayClient wxPayClient;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentService paymentService,
                             WxPayClient wxPayClient,
                             @Qualifier("webObjectMapper") ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.wxPayClient = wxPayClient;
        this.objectMapper = objectMapper;
    }

    // ─────────────────────── 创建订单 ───────────────────────

    /**
     * 创建会员订单
     * <p>调用微信统一下单，返回前端唤起 uni.requestPayment 所需的签名参数。
     * 若微信支付尚未配置（开发环境），返回的 wxPayParams 为 null，前端走 mock 流程。</p>
     *
     * POST /api/payment/order
     */
    @PostMapping("/order")
    public Result<VipOrderDTO> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Long userId = UserContext.getUserId();
        return Result.success(paymentService.createOrder(userId, request));
    }

    // ─────────────────────── 积分充值订单 ───────────────────────

    /**
     * 创建积分充值订单
     * POST /api/payment/point-order
     */
    @PostMapping("/point-order")
    public Result<PointOrderDTO> createPointOrder(@Valid @RequestBody CreatePointOrderRequest request) {
        Long userId = UserContext.getUserId();
        return Result.success(paymentService.createPointOrder(userId, request));
    }

    /**
     * 查询积分充值订单状态
     * GET /api/payment/point-order/{orderNo}
     */
    @GetMapping("/point-order/{orderNo}")
    public Result<PointOrderDTO> getPointOrder(@PathVariable("orderNo") String orderNo) {
        Long userId = UserContext.getUserId();
        return Result.success(paymentService.getPointOrder(userId, orderNo));
    }

    // ─────────────────────── 查询 VIP 订单 ───────────────────────

    /**
     * 查询 VIP 订单状态（前端轮询确认支付结果使用）
     *
     * GET /api/payment/order/{orderNo}
     */
    @GetMapping("/order/{orderNo}")
    public Result<VipOrderDTO> getOrder(@PathVariable("orderNo") String orderNo) {
        Long userId = UserContext.getUserId();
        return Result.success(paymentService.getOrder(userId, orderNo));
    }

    // ─────────────────────── 微信支付回调 ───────────────────────

    /**
     * 微信支付结果通知回调（不需要认证，已在拦截器白名单中排除）
     *
     * <p>微信支付 V3 回调流程：
     * <ol>
     *   <li>验证请求时效性（Wechatpay-Timestamp 与当前时间差不超过 5 分钟）</li>
     *   <li>使用平台证书公钥验证 Wechatpay-Signature 签名</li>
     *   <li>用 API V3 密钥对 resource.ciphertext 做 AES-256-GCM 解密，获取业务数据</li>
     *   <li>提取 out_trade_no / transaction_id，调用 Service 更新订单和 VIP 状态</li>
     *   <li>返回 {"code":"SUCCESS"} 告知微信处理成功，否则返回 FAIL 触发微信重推</li>
     * </ol>
     *
     * POST /api/payment/wx-callback
     */
    @PostMapping("/wx-callback")
    public String wxPayCallback(HttpServletRequest servletRequest,
                                @RequestBody String body) {
        // 1. 读取微信回调签名请求头
        String timestamp = servletRequest.getHeader("Wechatpay-Timestamp");
        String nonce = servletRequest.getHeader("Wechatpay-Nonce");
        String signature = servletRequest.getHeader("Wechatpay-Signature");
        String serial = servletRequest.getHeader("Wechatpay-Serial");

        log.info("WxPay callback received: serial={}, timestamp={}", serial, timestamp);

        // 2. 验证请求时效性（防重放，允许 ±5 分钟误差）
        if (StringUtils.hasText(timestamp)) {
            long callbackTime = Long.parseLong(timestamp);
            long now = System.currentTimeMillis() / 1000;
            if (Math.abs(now - callbackTime) > 300) {
                log.warn("WxPay callback timestamp expired: timestamp={}, now={}", callbackTime, now);
                return buildFailResponse("TIMESTAMP_EXPIRED");
            }
        }

        // 3. 验签（若签名信息完整）
        if (StringUtils.hasText(timestamp) && StringUtils.hasText(nonce) && StringUtils.hasText(signature)) {
            boolean valid = wxPayClient.verifyCallback(timestamp, nonce, signature, body);
            if (!valid) {
                log.warn("WxPay callback signature verification failed");
                return buildFailResponse("SIGN_ERROR");
            }
        } else {
            log.warn("WxPay callback missing signature headers, skip verify (dev mode)");
        }

        // 4. 解析回调 JSON
        try {
            JsonNode root = objectMapper.readTree(body);
            String eventType = safeText(root, "event_type");

            // 只处理支付成功事件
            if (!"TRANSACTION.SUCCESS".equals(eventType)) {
                log.info("WxPay callback event_type={}, skip", eventType);
                return buildSuccessResponse();
            }

            // 5. 解密 resource 字段
            JsonNode resource = root.get("resource");
            if (resource == null) {
                log.warn("WxPay callback missing resource field");
                return buildFailResponse("MISSING_RESOURCE");
            }

            String algorithm = safeText(resource, "algorithm");
            String ciphertext = safeText(resource, "ciphertext");
            String callbackNonce = safeText(resource, "nonce");
            String associatedData = safeText(resource, "associated_data");

            if (!StringUtils.hasText(ciphertext)) {
                log.warn("WxPay callback missing ciphertext");
                return buildFailResponse("MISSING_CIPHERTEXT");
            }

            String plaintext = wxPayClient.decryptCallback(algorithm, callbackNonce, associatedData, ciphertext);
            log.debug("WxPay callback decrypted: {}", plaintext);

            // 6. 提取业务字段
            JsonNode txNode = objectMapper.readTree(plaintext);
            String orderNo = safeText(txNode, "out_trade_no");
            String transactionId = safeText(txNode, "transaction_id");
            String tradeState = safeText(txNode, "trade_state");

            if (!StringUtils.hasText(orderNo)) {
                log.warn("WxPay callback missing out_trade_no");
                return buildFailResponse("MISSING_ORDER_NO");
            }

            // 只有 SUCCESS 状态才更新订单（其他状态如 REFUND、NOTPAY 等忽略）
            if (!"SUCCESS".equals(tradeState)) {
                log.info("WxPay callback trade_state={}, skip: orderNo={}", tradeState, orderNo);
                return buildSuccessResponse();
            }

            // 7. 根据订单号前缀路由到对应处理方法
            // VIP 订单前缀: VIP, 积分充值订单前缀: PT
            if (orderNo.startsWith("PT")) {
                paymentService.handlePointPayCallback(orderNo, transactionId);
                log.info("WxPay point-order callback handled: orderNo={}", orderNo);
            } else {
                paymentService.handlePayCallback(orderNo, transactionId);
                log.info("WxPay vip-order callback handled: orderNo={}", orderNo);
            }

        } catch (Exception e) {
            log.error("WxPay callback process error, body={}", body, e);
            return buildFailResponse("SYSTEM_ERROR");
        }

        return buildSuccessResponse();
    }

    // ─────────────────────── 私有工具方法 ───────────────────────

    /**
     * 安全地读取 JSON 节点中的字符串字段，字段不存在或为 null 时返回 null
     */
    private String safeText(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }

    /**
     * 构造微信支付回调的成功响应体
     * 微信接收到 {"code":"SUCCESS"} 后不再重推
     */
    private String buildSuccessResponse() {
        return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
    }

    /**
     * 构造微信支付回调的失败响应体
     * 微信接收到非 SUCCESS 响应后会在一定间隔内重新推送（最多推送 15 次）
     * 使用 ObjectMapper 构建 JSON，防止 message 中含引号等特殊字符时破坏 JSON 格式
     */
    private String buildFailResponse(String message) {
        try {
            return objectMapper.writeValueAsString(
                    java.util.Map.of("code", "FAIL", "message", message != null ? message : "FAIL")
            );
        } catch (Exception e) {
            return "{\"code\":\"FAIL\",\"message\":\"FAIL\"}";
        }
    }
}

