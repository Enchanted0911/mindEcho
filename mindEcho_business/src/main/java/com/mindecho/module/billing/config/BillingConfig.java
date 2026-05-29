package com.mindecho.module.billing.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 计费配置（支持运行时动态注入）
 *
 * <p>所有计费参数通过配置文件注入，便于运营后台动态调整而无需改代码。
 *
 * <p>积分换算：1 RMB = 100 积分
 */
@Getter
@Component
public class BillingConfig {

    // ─────────────────────── 基础费用（积分） ───────────────────────

    /** 普通聊天基础费用 */
    @Value("${billing.base-points.chat:5}")
    private long chatBasePoints;

    /** 占星解读基础费用（高级角色） */
    @Value("${billing.base-points.astrology:10}")
    private long astrologyBasePoints;

    /** 语音聊天基础费用 */
    @Value("${billing.base-points.voice:20}")
    private long voiceBasePoints;

    /** 图片生成基础费用 */
    @Value("${billing.base-points.image:100}")
    private long imageBasePoints;

    // ─────────────────────── 上下文阶梯费用（积分） ───────────────────────

    /** 上下文 2k~4k token 额外积分 */
    @Value("${billing.context-tiers.tier2k:2}")
    private long contextTier2k;

    /** 上下文 4k~8k token 额外积分 */
    @Value("${billing.context-tiers.tier4k:5}")
    private long contextTier4k;

    /** 上下文 8k~16k token 额外积分 */
    @Value("${billing.context-tiers.tier8k:10}")
    private long contextTier8k;

    /** 上下文 16k~32k token 额外积分 */
    @Value("${billing.context-tiers.tier16k:20}")
    private long contextTier16k;

    // ─────────────────────── 预扣安全缓冲 ───────────────────────

    /** 预扣安全缓冲比例（默认 30%，如 0.3） */
    @Value("${billing.pre-deduct.buffer-ratio:0.3}")
    private double preDeductBufferRatio;

    /** 预扣最低额外缓冲积分（默认 10） */
    @Value("${billing.pre-deduct.min-buffer:10}")
    private long preDeductMinBuffer;

    // ─────────────────────── 系统赠送 ───────────────────────

    /** 新用户注册赠送积分 */
    @Value("${billing.gift.new-user:100}")
    private long newUserGiftPoints;

    // ─────────────────────── 计费开关 ───────────────────────

    /** 是否启用计费（false 时跳过所有积分逻辑，便于开发调试） */
    @Value("${billing.enabled:true}")
    private boolean billingEnabled;

    /**
     * 根据上下文 token 数计算阶梯额外费用
     *
     * @param contextTokens 上下文 token 总数
     * @return 额外积分
     */
    public long calcContextPoints(int contextTokens) {
        if (contextTokens <= 2048) {
            return 0L;
        } else if (contextTokens <= 4096) {
            return contextTier2k;
        } else if (contextTokens <= 8192) {
            return contextTier4k;
        } else if (contextTokens <= 16384) {
            return contextTier8k;
        } else {
            return contextTier16k;
        }
    }

    /**
     * 计算预扣金额（含安全缓冲）
     *
     * @param estimatedPoints 预估积分
     * @return 预扣积分
     */
    public long calcPreDeductPoints(long estimatedPoints) {
        long buffer = Math.max(
                Math.round(estimatedPoints * preDeductBufferRatio),
                preDeductMinBuffer
        );
        return estimatedPoints + buffer;
    }
}

