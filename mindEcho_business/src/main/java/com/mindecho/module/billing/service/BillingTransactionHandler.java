package com.mindecho.module.billing.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mindecho.module.billing.config.BillingConfig;
import com.mindecho.module.billing.config.ModelBillingConfig;
import com.mindecho.module.billing.entity.AiUsageRecord;
import com.mindecho.module.billing.enums.UsageStatusEnum;
import com.mindecho.module.billing.mapper.AiUsageRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 计费事务处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingTransactionHandler {

    private final AiUsageRecordMapper usageRecordMapper;
    private final PointAccountService pointAccountService;
    private final BillingConfig billingConfig;
    private final ModelBillingConfig modelBillingConfig;

    @Transactional(rollbackFor = Exception.class)
    public void doSettle(UUID usageRecordId, UUID userId,
                         int promptTokens, int completionTokens,
                         String defaultModelName) {
        AiUsageRecord usageRec = usageRecordMapper.selectById(usageRecordId);
        if (usageRec == null) {
            log.error("UsageRecord not found for settlement: id={}", usageRecordId);
            return;
        }
        if (UsageStatusEnum.SUCCESS.getCode().equals(usageRec.getStatus())) {
            log.warn("UsageRecord already settled, skip: id={}", usageRecordId);
            return;
        }

        int totalTokens = promptTokens + completionTokens;
        long contextPoints = billingConfig.calcContextPoints(promptTokens);
        long basePoints = usageRec.getBasePoints() != null
                ? usageRec.getBasePoints()
                : getBasePoints(usageRec.getBusinessType());
        int multiplier = usageRec.getModelMultiplier() != null
                ? usageRec.getModelMultiplier()
                : modelBillingConfig.getMultiplier(defaultModelName);
        long actualPoints = modelBillingConfig.calcFinalPoints(basePoints, contextPoints, multiplier);
        long frozenAmount = usageRec.getEstimatedPoints() != null ? usageRec.getEstimatedPoints() : 0L;

        pointAccountService.settle(userId, frozenAmount, actualPoints, usageRecordId);

        usageRecordMapper.update(null,
                new LambdaUpdateWrapper<AiUsageRecord>()
                        .eq(AiUsageRecord::getId, usageRecordId)
                        .set(AiUsageRecord::getPromptTokens, promptTokens)
                        .set(AiUsageRecord::getCompletionTokens, completionTokens)
                        .set(AiUsageRecord::getTotalTokens, totalTokens)
                        .set(AiUsageRecord::getActualPoints, actualPoints)
                        .set(AiUsageRecord::getStatus, UsageStatusEnum.SUCCESS.getCode())
        );

        log.info("Billing settled: userId={}, usageId={}, prompt={}, completion={}, actualPoints={}",
                userId, usageRecordId, promptTokens, completionTokens, actualPoints);
    }

    @Transactional(rollbackFor = Exception.class)
    public void doFailAndRefund(UUID usageRecordId, UUID userId) {
        AiUsageRecord usageRec = usageRecordMapper.selectById(usageRecordId);
        if (usageRec == null || UsageStatusEnum.REFUNDED.getCode().equals(usageRec.getStatus())) {
            return;
        }

        usageRecordMapper.update(null,
                new LambdaUpdateWrapper<AiUsageRecord>()
                        .eq(AiUsageRecord::getId, usageRecordId)
                        .set(AiUsageRecord::getStatus, UsageStatusEnum.FAILED.getCode())
        );

        long frozenAmount = usageRec.getEstimatedPoints() != null ? usageRec.getEstimatedPoints() : 0L;
        if (frozenAmount > 0) {
            pointAccountService.refundFrozen(userId, frozenAmount, usageRecordId);
        }
        log.info("Billing refund: userId={}, usageId={}, refund={}", userId, usageRecordId, frozenAmount);
    }

    private long getBasePoints(String businessType) {
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
}

