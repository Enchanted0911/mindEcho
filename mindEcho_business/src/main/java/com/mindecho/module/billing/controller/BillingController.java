package com.mindecho.module.billing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mindecho.common.result.Result;
import com.mindecho.common.util.UserContext;
import com.mindecho.module.billing.dto.PointAccountDTO;
import com.mindecho.module.billing.dto.TransactionRecordDTO;
import com.mindecho.module.billing.dto.UsageRecordDTO;
import com.mindecho.module.billing.service.BillingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 积分计费 Controller
 *
 * <p>接口列表：
 * <ul>
 *   <li>GET  /api/billing/account          — 查询积分账户信息</li>
 *   <li>GET  /api/billing/transactions     — 查询积分流水（全部）</li>
 *   <li>GET  /api/billing/transactions/recharge — 查询充值流水</li>
 *   <li>GET  /api/billing/transactions/consume  — 查询消费流水</li>
 *   <li>GET  /api/billing/usage            — 查询 AI 使用量记录（含消费详情）</li>
 * </ul>
 */
@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingQueryService billingQueryService;

    // ─────────────────────── 积分账户 ───────────────────────

    /**
     * 查询当前用户积分账户信息（余额、冻结、累计统计）
     *
     * GET /api/billing/account
     */
    @GetMapping("/account")
    public Result<PointAccountDTO> getAccount() {
        UUID userId = UserContext.getUserId();
        return Result.success(billingQueryService.getAccountInfo(userId));
    }

    // ─────────────────────── 积分流水 ───────────────────────

    /**
     * 查询积分流水（全部类型，按时间倒序）
     *
     * GET /api/billing/transactions?page=1&size=20
     */
    @GetMapping("/transactions")
    public Result<IPage<TransactionRecordDTO>> getTransactions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        UUID userId = UserContext.getUserId();
        return Result.success(billingQueryService.getTransactionList(userId, page, size));
    }

    /**
     * 查询充值流水
     *
     * GET /api/billing/transactions/recharge?page=1&size=20
     */
    @GetMapping("/transactions/recharge")
    public Result<IPage<TransactionRecordDTO>> getRechargeList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        UUID userId = UserContext.getUserId();
        return Result.success(billingQueryService.getRechargeList(userId, page, size));
    }

    /**
     * 查询消费流水
     *
     * GET /api/billing/transactions/consume?page=1&size=20
     */
    @GetMapping("/transactions/consume")
    public Result<IPage<TransactionRecordDTO>> getConsumeList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        UUID userId = UserContext.getUserId();
        return Result.success(billingQueryService.getConsumeList(userId, page, size));
    }

    // ─────────────────────── AI 使用量记录 ───────────────────────

    /**
     * 查询 AI 使用量记录（含每次消费的模型、token、积分详情）
     *
     * GET /api/billing/usage?page=1&size=20
     */
    @GetMapping("/usage")
    public Result<IPage<UsageRecordDTO>> getUsageList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        UUID userId = UserContext.getUserId();
        return Result.success(billingQueryService.getUsageList(userId, page, size));
    }
}

