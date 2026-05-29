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
 *
 * <p>本服务可被 ChatService / AstrologyAiService 等业务服务注入调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final AiUsageRecordMapper usageRecordMapper;
    private final PointAccountService pointAccountService;
    private final BillingConfig billingConfig;
    private final ModelBillingConfig modelBillingConfig;

    /** 当前项目使用的模型名称（从 application.yml 注入） */
    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String defaultModelName;

    // ─────────────────────── 初始化 + 预扣 ───────────────────────

    /**
     * 创建 AI 使用记录并执行预扣积分
     *
     * <p>此方法在 AI 调用前调用，确保用户有足够积分。
     * 若余额不足，抛出 BusinessException(INSUFFICIENT_POINTS)。
     *
     * @param userId                 用户ID
     * @param sessionId              会话ID（可为 null）
     * @param businessType           业务类型（CHAT / ASTROLOGY_NATAL 等）
     * @param estimatedContextTokens 预估上下文 token 数
     * @return AI 使用记录（含 id，用于后续结算）
     */
    @Transactional
    public AiUsageRecord initUsageAndPreDeduct(Long userId, Long sessionId,
                                                String businessType, int estimatedContextTokens) {
        if (!billingConfig.isBillingEnabled()) {
            // 计费未启用，创建一个无积分的 usage 记录占位
            return createUsageRecord(userId, sessionId, businessType, estimatedContextTokens, 0L, 0L);
        }

        // 1. 计算预估积分
        long basePoints = getBasePoints(businessType);
        long contextPoints = billingConfig.calcContextPoints(estimatedContextTokens);
        int multiplier = modelBillingConfig.getMultiplier(defaultModelName);
        long estimatedPoints = modelBillingConfig.calcFinalPoints(basePoints, contextPoints, multiplier);
        long preDeductAmount = billingConfig.calcPreDeductPoints(estimatedPoints);

        // 2. 创建 usage 记录
        AiUsageRecord record = createUsageRecord(userId, sessionId, businessType,
                estimatedContextTokens, estimatedPoints, preDeductAmount);

        // 3. 预扣积分
        pointAccountService.preDeduct(userId, preDeductAmount, record.getId());

        log.info("Billing initiated: userId={}, type={}, contextTokens={}, estimated={}, preDeduct={}",
                userId, businessType, estimatedContextTokens, estimatedPoints, preDeductAmount);

        return record;
    }

    // ─────────────────────── 最终结算 ───────────────────────

    /**
     * 根据真实 token 用量最终结算积分
     *
     * <p>此方法在 AI 返回 usage 后异步调用（SSE 完成回调中）。
     * 幂等保护：若 usage 记录已为 SUCCESS，直接跳过。
     *
     * @param usageRecordId    ai_usage_record.id
     * @param userId           用户ID
     * @param promptTokens     实际输入 token
     * @param completionTokens 实际输出 token
     */
    @Async
    public void settleUsage(Long usageRecordId, Long userId,
                             int promptTokens, int completionTokens) {
        if (!billingConfig.isBillingEnabled()) {
            return;
        }

        try {
            AiUsageRecord record = usageRecordMapper.selectById(usageRecordId);
            if (record == null) {
                log.error("UsageRecord not found for settlement: id={}", usageRecordId);
                return;
            }

            // 幂等保护
            if (UsageStatusEnum.SUCCESS.getCode().equals(record.getStatus())) {
                log.warn("UsageRecord already settled, skip: id={}", usageRecordId);
                return;
            }

            // 计算实际积分
            int totalTokens = promptTokens + completionTokens;
            // 以实际 prompt tokens 作为上下文计费基准
            long contextPoints = billingConfig.calcContextPoints(promptTokens);
            long basePoints = record.getBasePoints() != null ? record.getBasePoints() : getBasePoints(record.getBusinessType());
            int multiplier = record.getModelMultiplier() != null ? record.getModelMultiplier()
                    : modelBillingConfig.getMultiplier(defaultModelName);
            long actualPoints = modelBillingConfig.calcFinalPoints(basePoints, contextPoints, multiplier);

            // 更新 usage 记录（结算字段）
            usageRecordMapper.update(null,
                    new LambdaUpdateWrapper<AiUsageRecord>()
                            .eq(AiUsageRecord::getId, usageRecordId)
                            .set(AiUsageRecord::getPromptTokens, promptTokens)
                            .set(AiUsageRecord::getCompletionTokens, completionTokens)
                            .set(AiUsageRecord::getTotalTokens, totalTokens)
                            .set(AiUsageRecord::getActualPoints, actualPoints)
                            .set(AiUsageRecord::getStatus, UsageStatusEnum.SUCCESS.getCode())
            );

            // 最终结算积分账户
            long preDeductAmount = record.getEstimatedPoints() != null ? record.getEstimatedPoints() : 0L;
            // 用 frozen 中的预扣量（estimatedPoints 已含安全缓冲）
            // 实际上账户冻结的是 preDeductPoints，但 record 存的是 estimatedPoints
            // 修正：account settle 需要实际冻结量，从 frozenPoints 字段获取
            // 由于之前创建 record 时用了 preDeductPoints（含缓冲），这里应该用 record 实际冻结的那个值
            // 改为在 record 中存 pre_deduct_points 字段来区分
            // 当前版本：estimated_points 存预扣量（preDeductAmount），actual_points 存最终量
            pointAccountService.settle(userId, record.getEstimatedPoints(), actualPoints, usageRecordId);

            log.info("Billing settled: userId={}, usageId={}, prompt={}, completion={}, actualPoints={}",
                    userId, usageRecordId, promptTokens, completionTokens, actualPoints);

        } catch (Exception e) {
            log.error("Billing settlement failed: usageId={}, userId={}", usageRecordId, userId, e);
            // 结算失败时，异步重试（此处简化：记录日志，后续可接 MQ 重试）
        }
    }

    // ─────────────────────── 失败退回 ───────────────────────

    /**
     * AI 调用失败，全额退回预扣积分
     *
     * @param usageRecordId ai_usage_record.id
     * @param userId        用户ID
     */
    @Async
    public void failAndRefund(Long usageRecordId, Long userId) {
        if (!billingConfig.isBillingEnabled()) {
            return;
        }

        try {
            AiUsageRecord record = usageRecordMapper.selectById(usageRecordId);
            if (record == null || UsageStatusEnum.REFUNDED.getCode().equals(record.getStatus())) {
                return;
            }

            // 更新状态为 FAILED
            usageRecordMapper.update(null,
                    new LambdaUpdateWrapper<AiUsageRecord>()
                            .eq(AiUsageRecord::getId, usageRecordId)
                            .set(AiUsageRecord::getStatus, UsageStatusEnum.FAILED.getCode())
            );

            long frozenAmount = record.getEstimatedPoints() != null ? record.getEstimatedPoints() : 0L;
            if (frozenAmount > 0) {
                pointAccountService.refundFrozen(userId, frozenAmount, usageRecordId);
            }
            log.info("Billing refund (AI failure): userId={}, usageId={}, refund={}", userId, usageRecordId, frozenAmount);
        } catch (Exception e) {
            log.error("Billing refund failed: usageId={}, userId={}", usageRecordId, userId, e);
        }
    }

    /**
     * Streaming 中断时按实际已产生 token 计费
     * 若已产生了部分 token，按实际收费；否则全额退回
     */
    @Async
    public void handleStreamingInterrupt(Long usageRecordId, Long userId,
                                          int partialPromptTokens, int partialCompletionTokens) {
        if (!billingConfig.isBillingEnabled()) {
            return;
        }

        // 标记 streaming 中断
        usageRecordMapper.update(null,
                new LambdaUpdateWrapper<AiUsageRecord>()
                        .eq(AiUsageRecord::getId, usageRecordId)
                        .set(AiUsageRecord::getStreamingInterrupted, true)
        );

        if (partialCompletionTokens > 0) {
            // 已产生内容，按实际计费
            settleUsage(usageRecordId, userId, partialPromptTokens, partialCompletionTokens);
        } else {
            // 未产生内容，全额退回
            failAndRefund(usageRecordId, userId);
        }
    }

    // ─────────────────────── 内部工具 ───────────────────────

    /**
     * 创建 AI 使用记录
     */
    private AiUsageRecord createUsageRecord(Long userId, Long sessionId, String businessType,
                                              int contextTokens, long estimatedPoints, long preDeductPoints) {
        int multiplier = modelBillingConfig.getMultiplier(defaultModelName);
        long basePoints = getBasePoints(businessType);
        long contextPointsVal = billingConfig.calcContextPoints(contextTokens);

        AiUsageRecord record = new AiUsageRecord();
        record.setRequestId(generateRequestId());
        record.setUserId(userId);
        record.setSessionId(sessionId);
        record.setBusinessType(businessType);
        record.setModelName(defaultModelName);
        record.setContextTokens(contextTokens);
        record.setBasePoints(basePoints);
        record.setContextPoints(contextPointsVal);
        record.setModelMultiplier(multiplier);
        // estimated_points 存储实际预扣量（含安全缓冲）
        record.setEstimatedPoints(preDeductPoints > 0 ? preDeductPoints : estimatedPoints);
        record.setActualPoints(null);
        record.setStatus(UsageStatusEnum.PRE_DEDUCTED.getCode());
        record.setStreamingInterrupted(false);
        usageRecordMapper.insert(record);
        return record;
    }

    /**
     * 根据业务类型获取基础积分
     */
    public long getBasePoints(String businessType) {
        if (businessType == null) return billingConfig.getChatBasePoints();
        return switch (businessType) {
            case "ASTROLOGY_NATAL", "ASTROLOGY_SYNASTRY", "ASTROLOGY_TRANSIT" ->
                    billingConfig.getAstrologyBasePoints();
            case "VOICE" -> billingConfig.getVoiceBasePoints();
            case "IMAGE" -> billingConfig.getImageBasePoints();
            default -> billingConfig.getChatBasePoints();
        };
    }

    private String generateRequestId() {
        return "REQ" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }
}

