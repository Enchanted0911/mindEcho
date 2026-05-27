package com.mindecho.module.payment.service;

import cn.hutool.core.util.IdUtil;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import com.mindecho.module.auth.entity.User;
import com.mindecho.module.auth.mapper.UserMapper;
import com.mindecho.module.payment.dto.CreateOrderRequest;
import com.mindecho.module.payment.dto.VipOrderDTO;
import com.mindecho.module.payment.entity.VipOrder;
import com.mindecho.module.payment.mapper.VipOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final VipOrderMapper vipOrderMapper;
    private final UserMapper userMapper;

    /** 价格配置 */
    private static final Map<String, BigDecimal> PRICE_MAP = Map.of(
            "monthly", new BigDecimal("9.90"),
            "quarterly", new BigDecimal("25.90"),
            "yearly", new BigDecimal("88.00")
    );

    /** 会员时长（天） */
    private static final Map<String, Integer> DURATION_MAP = Map.of(
            "monthly", 30,
            "quarterly", 90,
            "yearly", 365
    );

    /**
     * 创建订单
     */
    @Transactional
    public VipOrderDTO createOrder(Long userId, CreateOrderRequest request) {
        String vipType = request.getVipType();

        if (!PRICE_MAP.containsKey(vipType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        BigDecimal amount = PRICE_MAP.get(vipType);

        // 创建订单
        VipOrder order = new VipOrder();
        order.setUserId(userId);
        order.setOrderNo(generateOrderNo());
        order.setAmount(amount);
        order.setStatus("pending");
        order.setVipType(vipType);
        vipOrderMapper.insert(order);

        log.info("Order created: orderNo={}, userId={}, vipType={}, amount={}",
                order.getOrderNo(), userId, vipType, amount);

        // TODO: 调用微信支付 API 获取 prepay_id
        // 这里需要集成微信支付 V3 SDK，Key 留空
        // WxPayV3Request wxPayRequest = buildWxPayRequest(order, userId);
        // WxPayV3Response wxPayResponse = wxPayClient.unifiedOrder(wxPayRequest);

        return VipOrderDTO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .amount(order.getAmount())
                .status(order.getStatus())
                .vipType(order.getVipType())
                .createdTime(order.getCreatedTime())
                // .wxPayParams(buildWxPayParams(wxPayResponse))  // 待实现
                .build();
    }

    /**
     * 微信支付回调（支付成功）
     */
    @Transactional
    public void handlePayCallback(String orderNo) {
        VipOrder order = vipOrderMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        if ("paid".equals(order.getStatus())) {
            log.warn("Order already paid: orderNo={}", orderNo);
            return;
        }

        // 更新订单状态
        Integer days = DURATION_MAP.get(order.getVipType());
        LocalDateTime expireTime = LocalDateTime.now().plusDays(days);
        order.setStatus("paid");
        order.setExpireTime(expireTime);
        vipOrderMapper.updateById(order);

        // 更新用户 VIP 状态
        User user = userMapper.selectById(order.getUserId());
        if (user != null) {
            // 续期逻辑：已有有效 VIP 则延续
            LocalDateTime newExpireTime = user.getVipExpireTime() != null
                    && user.getVipExpireTime().isAfter(LocalDateTime.now())
                    ? user.getVipExpireTime().plusDays(days)
                    : expireTime;

            user.setVipExpireTime(newExpireTime);
            userMapper.updateById(user);
            log.info("User VIP updated: userId={}, expireTime={}", user.getId(), newExpireTime);
        }
    }

    /**
     * 查询订单
     */
    public VipOrderDTO getOrder(Long userId, String orderNo) {
        VipOrder order = vipOrderMapper.findByOrderNo(orderNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        return VipOrderDTO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .amount(order.getAmount())
                .status(order.getStatus())
                .vipType(order.getVipType())
                .expireTime(order.getExpireTime())
                .createdTime(order.getCreatedTime())
                .build();
    }

    private String generateOrderNo() {
        return "ME" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase();
    }
}

