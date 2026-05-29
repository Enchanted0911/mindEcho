package com.mindecho.module.billing.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mindecho.module.billing.config.BillingConfig;
import com.mindecho.module.billing.config.ModelBillingConfig;
import com.mindecho.module.billing.entity.AiUsageRecord;
import com.mindecho.module.billing.enums.UsageStatusEnum;
import com.mindecho.module.billing.mapper.AiUsageRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 计费服务
 *
 * <p>整合积分账户服务和 AI 用量记录，提供完整的 AI 调用计费流程：
 * <pre>
 * 1. initUsageAndPreDeduct  — 创建 usage 记录 + 预扣积分
 * 2. settleUsage            — 根据真实 token 用量最终结算
 * 3. failAndRefund          — AI 失败时全额退回
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final AiUsageRecordMapper usageRecordMapper;
    private final PointAccountService pointAccountService;
    private final BillingConfig billingConfig;
    private final ModelBillingConfig modelBillingConfig;
    private final BillingTransactionHandler txHandler;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String defaultModelName;

    // ─────────────────────── 初始化 + 预扣 ───────────────────────

    /**
     * 创建 AI 使用记录并执行预扣积分
     */
    @Transactional(rollbackFor = Exception.class)
    public AiUsageRecord initUsageAndPreDeduct(String userId, String sessionId,
                                                String businessType, int estimatedContextTokens) {
        if (!billingConfig.isBillingEnabled()) {
            return createUsageRecord(userId, sessionId, businessType, estimatedContextTokens, 0L, 0L);
        }

        long basePoints = getBasePoints(businessType);
        long contextPoints = billingConfig.calcContextPoints(estimatedContextTokens);
        int multiplier = modelBillingConfig.getMultiplier(defaultModelName);
        long estimatedPoints = modelBillingConfig.calcFinalPoints(basePoints, contextPoints, multiplier);
        long preDeductAmount = billingConfig.calcPreDeductPoints(estimatedPoints);

        AiUsageRecord usageRec = createUsageRecord(userId, sessionId, businessType,
                estimatedContextTokens, estimatedPoints, preDeductAmount);

        pointAccountService.preDeduct(userId, preDeductAmount, usageRec.getId());

        log.info("Billing initiated: userId={}, type={}, contextTokens={}, estimated={}, preDeduct={}",
                userId, businessType, estimatedContextTokens, estimatedPoints, preDeductAmount);
        return usageRec;
    }

    // ─────────────────────── 最终结算 ───────────────────────

    /**
     * 根据真实 token 用量最终结算积分（异步执行）
     */
    @Async
    public void settleUsage(String usageRecordId, String userId,
                             int promptTokens, int completionTokens) {
        if (!billingConfig.isBillingEnabled()) {
            return;
        }
        try {
            txHandler.doSettle(usageRecordId, userId, promptTokens, completionTokens, defaultModelName);
        } catch (Exception e) {
            log.error("Billing settlement failed: usageId={}, userId={}", usageRecordId, userId, e);
        }
    }

    // ─────────────────────── 失败退回 ───────────────────────

    /**
     * AI 调用失败，全额退回预扣积分（异步执行）
     */
    @Async
    public void failAndRefund(String usageRecordId, String userId) {
        if (!billingConfig.isBillingEnabled()) {
            return;
        }
        try {
            txHandler.doFailAndRefund(usageRecordId, userId);
        } catch (Exception e) {
            log.error("Billing refund failed: usageId={}, userId={}", usageRecordId, userId, e);
        }
    }

    /**
     * Streaming 中断时按实际已产生 token 计费（异步执行）
     */
    @Async
    public void handleStreamingInterrupt(String usageRecordId, String userId,
                                          int partialPromptTokens, int partialCompletionTokens) {
        if (!billingConfig.isBillingEnabled()) {
            return;
        }

        usageRecordMapper.update(null,
                new LambdaUpdateWrapper<AiUsageRecord>()
                        .eq(AiUsageRecord::getId, usageRecordId)
                        .set(AiUsageRecord::getStreamingInterrupted, true)
        );

        try {
            if (partialCompletionTokens > 0) {
                txHandler.doSettle(usageRecordId, userId, partialPromptTokens, partialCompletionTokens, defaultModelName);
            } else {
                txHandler.doFailAndRefund(usageRecordId, userId);
            }
        } catch (Exception e) {
            log.error("Billing interrupt handling failed: usageId={}, userId={}", usageRecordId, userId, e);
        }
    }

    // ─────────────────────── 工具方法 ───────────────────────

    public long getBasePoints(String businessType) {
        if (businessType == null) {
            return billingConfig.getChatBasePoints();
        }
        return switch (businessType) {
            case "ASTROLOGY_NATAL", "ASTROLOGY_SYNASTRY", "ASTROLOGY_TRANSIT" ->
                    billingConfig.getAstrologyBasePoints();
            case "VOICE" -> billingConfig.getVoiceBasePoints();
            case "IMAGE"  -> billingConfig.getImageBasePoints();
            default       -> billingConfig.getChatBasePoints();
        };
    }

    private AiUsageRecord createUsageRecord(String userId, String sessionId, String businessType,
                                              int contextTokens, long estimatedPoints, long preDeductPoints) {
        int multiplier = modelBillingConfig.getMultiplier(defaultModelName);
        long basePoints = getBasePoints(businessType);
        long contextPointsVal = billingConfig.calcContextPoints(contextTokens);

        AiUsageRecord usageRec = new AiUsageRecord();
        usageRec.setRequestId("REQ" + System.currentTimeMillis()
                + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase());
        usageRec.setUserId(userId);
        usageRec.setSessionId(sessionId);
        usageRec.setBusinessType(businessType);
        usageRec.setModelName(defaultModelName);
        usageRec.setContextTokens(contextTokens);
        usageRec.setBasePoints(basePoints);
        usageRec.setContextPoints(contextPointsVal);
        usageRec.setModelMultiplier(multiplier);
        usageRec.setEstimatedPoints(preDeductPoints > 0 ? preDeductPoints : estimatedPoints);
        usageRec.setActualPoints(null);
        usageRec.setStatus(UsageStatusEnum.PRE_DEDUCTED.getCode());
        usageRec.setStreamingInterrupted(false);
        usageRecordMapper.insert(usageRec);
        return usageRec;
    }
}

