package com.mindecho.module.payment.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import com.mindecho.module.auth.entity.User;
import com.mindecho.module.auth.mapper.UserMapper;
import com.mindecho.module.billing.service.PointAccountService;
import com.mindecho.module.payment.config.WxPayClient;
import com.mindecho.module.payment.config.WxPayConfig;
import com.mindecho.module.payment.dto.CreateOrderRequest;
import com.mindecho.module.payment.dto.CreatePointOrderRequest;
import com.mindecho.module.payment.dto.PointOrderDTO;
import com.mindecho.module.payment.dto.VipOrderDTO;
import com.mindecho.module.payment.entity.PointOrder;
import com.mindecho.module.payment.entity.VipOrder;
import com.mindecho.module.payment.mapper.PointOrderMapper;
import com.mindecho.module.payment.mapper.VipOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 支付服务
 *
 * <p>完整实现微信支付 V3（JSAPI 小程序支付）流程：
 * <ol>
 *   <li>createOrder — 创建会员订单，调用微信统一下单，返回前端唤起支付的签名参数</li>
 *   <li>createPointOrder — 创建积分充值订单</li>
 *   <li>handlePayCallback — 处理微信支付成功回调（VIP 订单）</li>
 *   <li>handlePointPayCallback — 处理积分充值支付成功回调</li>
 *   <li>getOrder — 查询 VIP 订单当前状态</li>
 *   <li>getPointOrder — 查询积分充值订单状态</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final VipOrderMapper vipOrderMapper;
    private final PointOrderMapper pointOrderMapper;
    private final UserMapper userMapper;
    private final WxPayClient wxPayClient;
    private final WxPayConfig wxPayConfig;
    private final PointAccountService pointAccountService;

    /** 小程序 appId（统一下单需要） */
    @Value("${wechat.appid}")
    private String appId;

    // ─────────────────────── VIP 套餐常量 ───────────────────────

    private static final String VIP_MONTHLY = "monthly";
    private static final String VIP_QUARTERLY = "quarterly";
    private static final String VIP_YEARLY = "yearly";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PAID = "paid";

    /** VIP 套餐价格（元） */
    private static final Map<String, BigDecimal> VIP_PRICE_MAP = Map.of(
            VIP_MONTHLY, new BigDecimal("9.90"),
            VIP_QUARTERLY, new BigDecimal("25.90"),
            VIP_YEARLY, new BigDecimal("88.00")
    );

    /** VIP 套餐时长（天） */
    private static final Map<String, Integer> VIP_DURATION_MAP = Map.of(
            VIP_MONTHLY, 30,
            VIP_QUARTERLY, 90,
            VIP_YEARLY, 365
    );

    /** VIP 套餐描述 */
    private static final Map<String, String> VIP_DESC_MAP = Map.of(
            VIP_MONTHLY, "心屿月度会员",
            VIP_QUARTERLY, "心屿季度会员",
            VIP_YEARLY, "心屿年度会员"
    );

    /**
     * VIP 购买时赠送的积分（鼓励用户购买 VIP）
     * monthly:300, quarterly:1000, yearly:4000
     */
    private static final Map<String, Long> VIP_GIFT_POINTS_MAP = Map.of(
            VIP_MONTHLY, 300L,
            VIP_QUARTERLY, 1000L,
            VIP_YEARLY, 4000L
    );

    // ─────────────────────── 积分套餐常量 ───────────────────────

    /**
     * 积分套餐配置：packageType -> [price, points]
     * 1 RMB = 100 积分（基础换算）
     * 大包有赠送（超值优惠）
     */
    private static final Map<String, long[]> POINT_PACKAGE_MAP = Map.of(
            "small",       new long[]{6_00L,   100L},   // 6元  = 100积分（无赠送）
            "medium",      new long[]{30_00L,  550L},   // 30元 = 500+50积分（赠10%）
            "large",       new long[]{98_00L, 1200L},   // 98元 = 1000+200积分（赠20%）
            "extra_large", new long[]{198_00L, 2500L}   // 198元= 2000+500积分（赠25%）
    );

    private static final Map<String, String> POINT_PACKAGE_DESC_MAP = Map.of(
            "small",       "心屿积分·入门包（100积分）",
            "medium",      "心屿积分·超值包（550积分）",
            "large",       "心屿积分·豪华包（1200积分）",
            "extra_large", "心屿积分·旗舰包（2500积分）"
    );

    // ─────────────────────── 创建 VIP 订单 ───────────────────────

    /**
     * 创建会员订单并调用微信统一下单
     */
    @Transactional
    public VipOrderDTO createOrder(String userId, CreateOrderRequest request) {
        String vipType = request.getVipType();

        if (!VIP_PRICE_MAP.containsKey(vipType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        BigDecimal amount = VIP_PRICE_MAP.get(vipType);
        String desc = VIP_DESC_MAP.get(vipType);

        // 复用 pending 订单
        VipOrder order = vipOrderMapper.selectOne(
                new LambdaQueryWrapper<VipOrder>()
                        .eq(VipOrder::getUserId, userId)
                        .eq(VipOrder::getVipType, vipType)
                        .eq(VipOrder::getStatus, STATUS_PENDING)
                        .eq(VipOrder::getDeleted, 0)
                        .orderByDesc(VipOrder::getCreatedTime)
                        .last("LIMIT 1")
        );

        if (order == null) {
            order = new VipOrder();
            order.setUserId(userId);
            order.setOrderNo(generateOrderNo("VIP"));
            order.setAmount(amount);
            order.setStatus(STATUS_PENDING);
            order.setVipType(vipType);
            vipOrderMapper.insert(order);
            log.info("VIP order created: orderNo={}, userId={}, vipType={}, amount={}",
                    order.getOrderNo(), userId, vipType, amount);
        }

        String prepayId = null;
        VipOrderDTO.WxPayParams wxPayParams = null;

        if (isWxPayConfigured()) {
            try {
                prepayId = wxPayClient.unifiedOrderJsapi(
                        appId, user.getOpenid(), order.getOrderNo(), amount, desc);
                order.setPrepayId(prepayId);
                vipOrderMapper.updateById(order);

                WxPayClient.WxPaySignParams signParams = wxPayClient.buildMiniPayParams(appId, prepayId);
                wxPayParams = VipOrderDTO.WxPayParams.builder()
                        .timeStamp(signParams.getTimeStamp())
                        .nonceStr(signParams.getNonceStr())
                        .pkg(signParams.getPkg())
                        .signType(signParams.getSignType())
                        .paySign(signParams.getPaySign())
                        .build();
            } catch (Exception e) {
                log.error("WxPay unifiedOrder failed: orderNo={}", order.getOrderNo(), e);
                throw new BusinessException(ResultCode.PAYMENT_FAILED);
            }
        }

        return VipOrderDTO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .amount(order.getAmount())
                .status(order.getStatus())
                .vipType(order.getVipType())
                .createdTime(order.getCreatedTime())
                .wxPayParams(wxPayParams)
                .build();
    }

    // ─────────────────────── 创建积分充值订单 ───────────────────────

    /**
     * 创建积分充值订单并调用微信统一下单
     */
    @Transactional
    public PointOrderDTO createPointOrder(String userId, CreatePointOrderRequest request) {
        String packageType = request.getPackageType();
        long[] packageInfo = POINT_PACKAGE_MAP.get(packageType);
        if (packageInfo == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 金额：分转元
        BigDecimal amount = new BigDecimal(packageInfo[0]).movePointLeft(2);
        long points = packageInfo[1];
        String desc = POINT_PACKAGE_DESC_MAP.getOrDefault(packageType, "心屿积分充值");

        // 复用 pending 订单
        PointOrder order = pointOrderMapper.selectOne(
                new LambdaQueryWrapper<PointOrder>()
                        .eq(PointOrder::getUserId, userId)
                        .eq(PointOrder::getPackageType, packageType)
                        .eq(PointOrder::getStatus, STATUS_PENDING)
                        .eq(PointOrder::getDeleted, 0)
                        .orderByDesc(PointOrder::getCreatedTime)
                        .last("LIMIT 1")
        );

        if (order == null) {
            order = new PointOrder();
            order.setUserId(userId);
            order.setOrderNo(generateOrderNo("PT"));
            order.setAmount(amount);
            order.setPoints(points);
            order.setPackageType(packageType);
            order.setStatus(STATUS_PENDING);
            order.setPayChannel("wechat");
            pointOrderMapper.insert(order);
            log.info("Point order created: orderNo={}, userId={}, package={}, points={}",
                    order.getOrderNo(), userId, packageType, points);
        }

        VipOrderDTO.WxPayParams wxPayParams = null;

        if (isWxPayConfigured()) {
            try {
                String prepayId = wxPayClient.unifiedOrderJsapi(
                        appId, user.getOpenid(), order.getOrderNo(), amount, desc);
                order.setPrepayId(prepayId);
                pointOrderMapper.updateById(order);

                WxPayClient.WxPaySignParams signParams = wxPayClient.buildMiniPayParams(appId, prepayId);
                wxPayParams = VipOrderDTO.WxPayParams.builder()
                        .timeStamp(signParams.getTimeStamp())
                        .nonceStr(signParams.getNonceStr())
                        .pkg(signParams.getPkg())
                        .signType(signParams.getSignType())
                        .paySign(signParams.getPaySign())
                        .build();
            } catch (Exception e) {
                log.error("WxPay unifiedOrder failed for point order: orderNo={}", order.getOrderNo(), e);
                throw new BusinessException(ResultCode.PAYMENT_FAILED);
            }
        }

        return PointOrderDTO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .amount(order.getAmount())
                .points(order.getPoints())
                .packageType(order.getPackageType())
                .status(order.getStatus())
                .createdTime(order.getCreatedTime())
                .wxPayParams(wxPayParams)
                .build();
    }

    // ─────────────────────── VIP 支付回调 ───────────────────────

    /**
     * 处理微信支付成功回调（VIP 订单）
     */
    @Transactional
    public void handlePayCallback(String orderNo, String transactionId) {
        VipOrder order = vipOrderMapper.selectOne(
                new LambdaQueryWrapper<VipOrder>()
                        .eq(VipOrder::getOrderNo, orderNo)
                        .eq(VipOrder::getDeleted, 0)
        );
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }

        if (STATUS_PAID.equals(order.getStatus())) {
            log.warn("VIP order already paid, skip: orderNo={}", orderNo);
            return;
        }

        if (!STATUS_PENDING.equals(order.getStatus())) {
            log.warn("VIP order status is not pending: orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }

        Integer days = VIP_DURATION_MAP.get(order.getVipType());
        if (days == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        OffsetDateTime expireTime = OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(days);
        order.setStatus(STATUS_PAID);
        order.setExpireTime(expireTime);
        if (transactionId != null && !transactionId.isBlank()) {
            order.setTransactionId(transactionId);
        }
        vipOrderMapper.updateById(order);
        log.info("VIP order paid: orderNo={}, transactionId={}", orderNo, transactionId);

        // 更新用户 VIP 到期时间
        User user = userMapper.selectById(order.getUserId());
        if (user == null) {
            log.error("User not found when updating VIP: userId={}", order.getUserId());
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Shanghai"));
        OffsetDateTime base = (user.getVipExpireTime() != null
                && user.getVipExpireTime().isAfter(now))
                ? user.getVipExpireTime() : now;
        user.setVipExpireTime(base.plusDays(days));
        userMapper.updateById(user);
        log.info("User VIP updated: userId={}, newExpireTime={}", user.getId(), user.getVipExpireTime());

        // 购买 VIP 赠送积分
        Long giftPoints = VIP_GIFT_POINTS_MAP.getOrDefault(order.getVipType(), 0L);
        if (giftPoints > 0) {
            try {
                pointAccountService.gift(order.getUserId(), giftPoints,
                        "购买" + VIP_DESC_MAP.getOrDefault(order.getVipType(), "VIP") + "赠送积分");
                log.info("VIP gift points: userId={}, points={}", order.getUserId(), giftPoints);
            } catch (Exception e) {
                log.error("VIP gift points failed: userId={}", order.getUserId(), e);
            }
        }
    }

    // ─────────────────────── 积分充值支付回调 ───────────────────────

    /**
     * 处理积分充值支付成功回调
     */
    @Transactional
    public void handlePointPayCallback(String orderNo, String transactionId) {
        PointOrder order = pointOrderMapper.selectOne(
                new LambdaQueryWrapper<PointOrder>()
                        .eq(PointOrder::getOrderNo, orderNo)
                        .eq(PointOrder::getDeleted, 0)
        );
        if (order == null) {
            throw new BusinessException(ResultCode.POINT_ORDER_NOT_FOUND);
        }

        if (STATUS_PAID.equals(order.getStatus())) {
            log.warn("Point order already paid, skip: orderNo={}", orderNo);
            return;
        }

        if (!STATUS_PENDING.equals(order.getStatus())) {
            log.warn("Point order status is not pending: orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }

        order.setStatus(STATUS_PAID);
        if (transactionId != null && !transactionId.isBlank()) {
            order.setTransactionId(transactionId);
        }
        pointOrderMapper.updateById(order);
        log.info("Point order paid: orderNo={}, transactionId={}, points={}", orderNo, transactionId, order.getPoints());

        // 充值积分
        pointAccountService.recharge(order.getUserId(), order.getPoints(), order.getId(),
                "充值" + POINT_PACKAGE_DESC_MAP.getOrDefault(order.getPackageType(), "积分包"));
    }

    // ─────────────────────── 查询订单 ───────────────────────

    public VipOrderDTO getOrder(String userId, String orderNo) {
        VipOrder order = vipOrderMapper.selectOne(
                new LambdaQueryWrapper<VipOrder>()
                        .eq(VipOrder::getOrderNo, orderNo)
                        .eq(VipOrder::getDeleted, 0)
        );
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        return toVipDTO(order);
    }

    public PointOrderDTO getPointOrder(String userId, String orderNo) {
        PointOrder order = pointOrderMapper.selectOne(
                new LambdaQueryWrapper<PointOrder>()
                        .eq(PointOrder::getOrderNo, orderNo)
                        .eq(PointOrder::getDeleted, 0)
        );
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.POINT_ORDER_NOT_FOUND);
        }
        return toPointDTO(order);
    }

    // ─────────────────────── 私有工具方法 ───────────────────────

    private boolean isWxPayConfigured() {
        return wxPayConfig.getMchId() != null && !wxPayConfig.getMchId().isBlank()
                && wxPayConfig.getPrivateKey() != null && !wxPayConfig.getPrivateKey().isBlank()
                && wxPayConfig.getApiV3Key() != null && !wxPayConfig.getApiV3Key().isBlank();
    }

    private VipOrderDTO toVipDTO(VipOrder order) {
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

    private PointOrderDTO toPointDTO(PointOrder order) {
        return PointOrderDTO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .amount(order.getAmount())
                .points(order.getPoints())
                .packageType(order.getPackageType())
                .status(order.getStatus())
                .createdTime(order.getCreatedTime())
                .build();
    }

    private String generateOrderNo(String prefix) {
        return prefix + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase();
    }
}

