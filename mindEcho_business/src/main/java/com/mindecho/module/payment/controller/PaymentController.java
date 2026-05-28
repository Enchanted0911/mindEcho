package com.mindecho.module.payment.controller;

import com.mindecho.common.result.Result;
import com.mindecho.common.util.UserContext;
import com.mindecho.module.payment.dto.CreateOrderRequest;
import com.mindecho.module.payment.dto.VipOrderDTO;
import com.mindecho.module.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 支付 Controller
 */
@Slf4j
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 创建会员订单
     * POST /api/payment/order
     */
    @PostMapping("/order")
    public Result<VipOrderDTO> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Long userId = UserContext.getUserId();
        return Result.success(paymentService.createOrder(userId, request));
    }

    /**
     * 查询订单状态
     * GET /api/payment/order/{orderNo}
     */
    @GetMapping("/order/{orderNo}")
    public Result<VipOrderDTO> getOrder(@PathVariable("orderNo") String orderNo) {
        Long userId = UserContext.getUserId();
        return Result.success(paymentService.getOrder(userId, orderNo));
    }

    /**
     * 微信支付回调（不需要认证）
     * POST /api/payment/wx-callback
     */
    @PostMapping("/wx-callback")
    public String wxPayCallback(@RequestBody String body) {
        // TODO: 解析微信支付回调签名验证
        log.info("WeChat pay callback received");
        // paymentService.handlePayCallback(orderNo);
        return "SUCCESS";
    }
}

