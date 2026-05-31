package com.mindecho.module.billing.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import com.mindecho.module.billing.config.BillingConfig;
import com.mindecho.module.billing.config.ModelBillingConfig;
import com.mindecho.module.billing.entity.AiUsageRecord;
import com.mindecho.module.billing.entity.UserPointAccount;
import com.mindecho.module.billing.enums.UsageStatusEnum;
import com.mindecho.module.billing.mapper.AiUsageRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * AI 计费服务
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

    // ─────────────────────── 余额预检 ───────────────────────

    /**
     * 提前检查用户积分余额是否足够发起一次 AI 请求。
     *
     * <p>在 session / message 写入之前调用，避免因积分不足在数据库留下无效数据。
     * 该方法仅做只读查询，不产生任何写入操作。
     *
     * @param userId       用户 ID
     * @param businessType 业务类型（如 "CHAT"）
     * @throws BusinessException INSUFFICIENT_POINTS 当余额不足时
     */
    public void checkBalanceBeforeRequest(UUID userId, String businessType) {
        if (!billingConfig.isBillingEnabled()) {
            return;
        }
        long basePoints = getBasePoints(businessType);
        int multiplier = modelBillingConfig.getMultiplier(defaultModelName);
        // 使用基础积分（不含上下文阶梯费用）作为最低门槛估算，保守判断
        long minRequired = modelBillingConfig.calcFinalPoints(basePoints, 0L, multiplier);
        long preDeductMin = billingConfig.calcPreDeductPoints(minRequired);

        UserPointAccount account = pointAccountService.getAccount(userId);
        if (!account.hasSufficientBalance(preDeductMin)) {
            log.warn("Pre-check insufficient balance: userId={}, balance={}, minRequired={}",
                    userId, account.getBalance(), preDeductMin);
            throw new BusinessException(ResultCode.INSUFFICIENT_POINTS);
        }
    }

    // ─────────────────────── 初始化 + 预扣 ───────────────────────

    @Transactional(rollbackFor = Exception.class)
    public AiUsageRecord initUsageAndPreDeduct(UUID userId, UUID sessionId,
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

    @Async
    public void settleUsage(UUID usageRecordId, UUID userId,
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

    @Async
    public void failAndRefund(UUID usageRecordId, UUID userId) {
        if (!billingConfig.isBillingEnabled()) {
            return;
        }
        try {
            txHandler.doFailAndRefund(usageRecordId, userId);
        } catch (Exception e) {
            log.error("Billing refund failed: usageId={}, userId={}", usageRecordId, userId, e);
        }
    }

    @Async
    public void handleStreamingInterrupt(UUID usageRecordId, UUID userId,
                                          int partialPromptTokens, int partialCompletionTokens) {
        if (!billingConfig.isBillingEnabled()) {
            return;
        }

        usageRecordMapper.update(null,
                new LambdaUpdateWrapper<AiUsageRecord>()
                        .eq(AiUsageRecord::getId, usageRecordId)
                        .set(AiUsageRecord::getStreamingInterrupted, 1)
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

    private AiUsageRecord createUsageRecord(UUID userId, UUID sessionId, String businessType,
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
        usageRec.setStreamingInterrupted(0);
        usageRecordMapper.insert(usageRec);
        return usageRec;
    }
}

