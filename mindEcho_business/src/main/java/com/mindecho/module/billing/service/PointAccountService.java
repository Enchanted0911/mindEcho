package com.mindecho.module.billing.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import com.mindecho.module.billing.config.BillingConfig;
import com.mindecho.module.billing.entity.PointTransaction;
import com.mindecho.module.billing.entity.UserPointAccount;
import com.mindecho.module.billing.enums.TransactionTypeEnum;
import com.mindecho.module.billing.mapper.PointTransactionMapper;
import com.mindecho.module.billing.mapper.UserPointAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 积分账户服务
 *
 * <p>负责用户积分账户的全生命周期管理：
 * <ul>
 *   <li>账户初始化（新用户注册时创建）</li>
 *   <li>预扣积分（AI 请求发起前冻结）</li>
 *   <li>最终结算（根据实际 token 扣费并退回多余积分）</li>
 *   <li>全额退回（AI 失败时）</li>
 *   <li>充值（购买积分包时增加余额）</li>
 *   <li>系统赠送（新用户礼包等）</li>
 * </ul>
 *
 * <p>所有扣费操作均在数据库事务中执行，通过 WHERE balance >= #{amount} 条件防止超扣。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointAccountService {

    private final UserPointAccountMapper accountMapper;
    private final PointTransactionMapper transactionMapper;
    private final BillingConfig billingConfig;

    private static final String TX_STATUS_SUCCESS = "success";

    // ─────────────────────── 账户初始化 ───────────────────────

    /**
     * 获取用户积分账户，若不存在则初始化（新用户注册时调用）
     * 初始赠送 newUserGiftPoints 积分
     */
    @Transactional
    public UserPointAccount getOrCreateAccount(String userId) {
        UserPointAccount account = findByUserId(userId);
        if (account != null) {
            return account;
        }

        // 创建新账户
        account = new UserPointAccount();
        account.setUserId(userId);
        account.setBalance(billingConfig.getNewUserGiftPoints());
        account.setFrozenBalance(0L);
        account.setTotalRecharge(0L);
        account.setTotalConsume(0L);
        account.setTotalRefund(0L);
        account.setTotalGift(billingConfig.getNewUserGiftPoints());
        accountMapper.insert(account);

        // 记录赠送流水
        if (billingConfig.getNewUserGiftPoints() > 0) {
            recordTransaction(userId, billingConfig.getNewUserGiftPoints(),
                    TransactionTypeEnum.SYSTEM_GIFT, 0L, billingConfig.getNewUserGiftPoints(),
                    null, "新用户注册礼包");
        }

        log.info("Point account created: userId={}, giftPoints={}", userId, billingConfig.getNewUserGiftPoints());
        return account;
    }

    /**
     * 查询账户（不存在时自动创建）
     */
    public UserPointAccount getAccount(String userId) {
        UserPointAccount account = findByUserId(userId);
        if (account == null) {
            return getOrCreateAccount(userId);
        }
        return account;
    }

    // ─────────────────────── 预扣 ───────────────────────

    /**
     * 预扣积分（发起 AI 请求前调用）
     *
     * @param userId          用户ID（UUID 字符串）
     * @param preDeductAmount 预扣积分数（含安全缓冲）
     * @param usageRecordId   ai_usage_record.id（UUID 字符串）
     */
    @Transactional
    public void preDeduct(String userId, long preDeductAmount, String usageRecordId) {
        if (!billingConfig.isBillingEnabled()) {
            return;
        }

        UserPointAccount account = findByUserId(userId);
        if (account == null) {
            account = getOrCreateAccount(userId);
        }

        if (!account.hasSufficientBalance(preDeductAmount)) {
            log.warn("Insufficient balance: userId={}, balance={}, required={}",
                    userId, account.getBalance(), preDeductAmount);
            throw new BusinessException(ResultCode.INSUFFICIENT_POINTS);
        }

        long beforeBalance = account.getBalance();

        int rows = accountMapper.update(null,
                new UpdateWrapper<UserPointAccount>()
                        .setSql("balance = balance - " + preDeductAmount
                                + ", frozen_balance = frozen_balance + " + preDeductAmount
                                + ", updated_time = CURRENT_TIMESTAMP")
                        .eq("user_id", userId)
                        .ge("balance", preDeductAmount)
                        .eq("deleted", 0)
        );

        if (rows == 0) {
            log.warn("PreDeduct failed (concurrent conflict): userId={}, amount={}", userId, preDeductAmount);
            throw new BusinessException(ResultCode.INSUFFICIENT_POINTS);
        }

        recordTransaction(userId, -preDeductAmount,
                TransactionTypeEnum.PRE_DEDUCT,
                beforeBalance, beforeBalance - preDeductAmount,
                usageRecordId, "AI请求预扣");

        log.info("PreDeduct success: userId={}, amount={}, usageRecordId={}", userId, preDeductAmount, usageRecordId);
    }

    // ─────────────────────── 最终结算 ───────────────────────

    /**
     * 最终结算（AI 完成后调用）
     *
     * @param userId        用户ID（UUID 字符串）
     * @param frozenAmount  预扣冻结积分数
     * @param actualAmount  实际消费积分数
     * @param usageRecordId ai_usage_record.id（UUID 字符串）
     */
    @Transactional
    public void settle(String userId, long frozenAmount, long actualAmount, String usageRecordId) {
        if (!billingConfig.isBillingEnabled()) {
            return;
        }

        UserPointAccount account = findByUserId(userId);
        if (account == null) {
            log.error("Account not found when settling: userId={}", userId);
            return;
        }

        if (actualAmount <= frozenAmount) {
            long refundAmount = frozenAmount - actualAmount;
            int rows = accountMapper.update(null,
                    new UpdateWrapper<UserPointAccount>()
                            .setSql("frozen_balance = frozen_balance - " + frozenAmount
                                    + ", balance = balance + " + refundAmount
                                    + ", total_consume = total_consume + " + actualAmount
                                    + ", total_refund = total_refund + " + refundAmount
                                    + ", updated_time = CURRENT_TIMESTAMP")
                            .eq("user_id", userId)
                            .ge("frozen_balance", frozenAmount)
                            .eq("deleted", 0)
            );

            if (rows == 0) {
                log.error("Settle failed: userId={}, frozenAmount={}", userId, frozenAmount);
                return;
            }
            long beforeBalance = account.getBalance();

            if (actualAmount > 0) {
                recordTransaction(userId, -actualAmount, TransactionTypeEnum.CONSUME,
                        beforeBalance, beforeBalance - actualAmount + refundAmount,
                        usageRecordId, "AI消费结算");
            }

            if (refundAmount > 0) {
                recordTransaction(userId, refundAmount, TransactionTypeEnum.REFUND,
                        beforeBalance, beforeBalance + refundAmount - actualAmount,
                        usageRecordId, "预扣多余退回");
            }

            log.info("Settle success: userId={}, frozen={}, actual={}, refund={}",
                    userId, frozenAmount, actualAmount, refundAmount);
        } else {
            long extraAmount = actualAmount - frozenAmount;
            accountMapper.update(null,
                    new UpdateWrapper<UserPointAccount>()
                            .setSql("frozen_balance = frozen_balance - " + frozenAmount
                                    + ", total_consume = total_consume + " + frozenAmount
                                    + ", updated_time = CURRENT_TIMESTAMP")
                            .eq("user_id", userId)
                            .ge("frozen_balance", frozenAmount)
                            .eq("deleted", 0)
            );
            accountMapper.update(null,
                    new UpdateWrapper<UserPointAccount>()
                            .setSql("balance = balance - " + extraAmount
                                    + ", total_consume = total_consume + " + extraAmount
                                    + ", updated_time = CURRENT_TIMESTAMP")
                            .eq("user_id", userId)
                            .eq("deleted", 0)
            );

            long beforeBalance = account.getBalance();
            recordTransaction(userId, -actualAmount, TransactionTypeEnum.CONSUME,
                    beforeBalance, beforeBalance - actualAmount,
                    usageRecordId, "AI消费结算（含超额补扣）");

            log.warn("Settle with extra deduct: userId={}, frozen={}, actual={}, extra={}",
                    userId, frozenAmount, actualAmount, extraAmount);
        }
    }

    // ─────────────────────── 全额退回 ───────────────────────

    /**
     * 全额退回冻结积分（AI 失败或未产生内容时）
     *
     * @param userId        用户ID（UUID 字符串）
     * @param frozenAmount  需退回的冻结积分
     * @param usageRecordId ai_usage_record.id（UUID 字符串）
     */
    @Transactional
    public void refundFrozen(String userId, long frozenAmount, String usageRecordId) {
        if (!billingConfig.isBillingEnabled() || frozenAmount <= 0) {
            return;
        }

        UserPointAccount account = findByUserId(userId);
        if (account == null) return;

        int rows = accountMapper.update(null,
                new UpdateWrapper<UserPointAccount>()
                        .setSql("balance = balance + " + frozenAmount
                                + ", frozen_balance = frozen_balance - " + frozenAmount
                                + ", total_refund = total_refund + " + frozenAmount
                                + ", updated_time = CURRENT_TIMESTAMP")
                        .eq("user_id", userId)
                        .ge("frozen_balance", frozenAmount)
                        .eq("deleted", 0)
        );

        if (rows == 0) {
            log.error("RefundFrozen failed: userId={}, amount={}", userId, frozenAmount);
            return;
        }

        long beforeBalance = account.getBalance();
        recordTransaction(userId, frozenAmount, TransactionTypeEnum.REFUND,
                beforeBalance, beforeBalance + frozenAmount,
                usageRecordId, "AI异常全额退回");

        log.info("RefundFrozen success: userId={}, amount={}", userId, frozenAmount);
    }

    // ─────────────────────── 充值 ───────────────────────

    /**
     * 积分充值（支付成功后调用）
     *
     * @param userId     用户ID（UUID 字符串）
     * @param amount     充值积分数
     * @param businessId 充值订单ID（UUID 字符串）
     * @param remark     备注
     */
    @Transactional
    public void recharge(String userId, long amount, String businessId, String remark) {
        UserPointAccount account = getAccount(userId);
        int rows = accountMapper.update(null,
                new UpdateWrapper<UserPointAccount>()
                        .setSql("balance = balance + " + amount
                                + ", total_recharge = total_recharge + " + amount
                                + ", updated_time = CURRENT_TIMESTAMP")
                        .eq("user_id", userId)
                        .eq("deleted", 0)
        );
        if (rows == 0) {
            log.error("Recharge failed: userId={}, amount={}", userId, amount);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }

        long beforeBalance = account.getBalance();
        recordTransaction(userId, amount, TransactionTypeEnum.RECHARGE,
                beforeBalance, beforeBalance + amount,
                businessId, remark != null ? remark : "积分充值");

        log.info("Recharge success: userId={}, amount={}, businessId={}", userId, amount, businessId);
    }

    /**
     * 系统赠送积分
     */
    @Transactional
    public void gift(String userId, long amount, String remark) {
        UserPointAccount account = getAccount(userId);
        int rows = accountMapper.update(null,
                new UpdateWrapper<UserPointAccount>()
                        .setSql("balance = balance + " + amount
                                + ", total_gift = total_gift + " + amount
                                + ", updated_time = CURRENT_TIMESTAMP")
                        .eq("user_id", userId)
                        .eq("deleted", 0)
        );
        if (rows == 0) {
            log.error("Gift failed: userId={}, amount={}", userId, amount);
            return;
        }
        long beforeBalance = account.getBalance();
        recordTransaction(userId, amount, TransactionTypeEnum.SYSTEM_GIFT,
                beforeBalance, beforeBalance + amount,
                null, remark != null ? remark : "系统赠送");
        log.info("Gift success: userId={}, amount={}", userId, amount);
    }

    // ─────────────────────── 内部工具 ───────────────────────

    /**
     * 根据用户 ID 查询账户
     */
    private UserPointAccount findByUserId(String userId) {
        return accountMapper.selectOne(
                new LambdaQueryWrapper<UserPointAccount>()
                        .eq(UserPointAccount::getUserId, userId)
                        .eq(UserPointAccount::getDeleted, 0)
        );
    }

    /**
     * 写积分流水记录
     */
    private void recordTransaction(String userId, long amount, TransactionTypeEnum type,
                                    long beforeBalance, long afterBalance,
                                    String businessId, String remark) {
        PointTransaction tx = new PointTransaction();
        tx.setTransactionNo(generateTransactionNo());
        tx.setUserId(userId);
        tx.setAmount(amount);
        tx.setType(type.getCode());
        tx.setBeforeBalance(beforeBalance);
        tx.setAfterBalance(afterBalance);
        tx.setBusinessId(businessId);
        tx.setRemark(remark);
        tx.setStatus(TX_STATUS_SUCCESS);
        transactionMapper.insert(tx);
    }

    private String generateTransactionNo() {
        return "PT" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase();
    }
}

