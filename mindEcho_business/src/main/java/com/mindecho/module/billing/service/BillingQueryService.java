package com.mindecho.module.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mindecho.module.billing.dto.PointAccountDTO;
import com.mindecho.module.billing.dto.TransactionRecordDTO;
import com.mindecho.module.billing.dto.UsageRecordDTO;
import com.mindecho.module.billing.entity.AiUsageRecord;
import com.mindecho.module.billing.entity.PointTransaction;
import com.mindecho.module.billing.entity.UserPointAccount;
import com.mindecho.module.billing.enums.TransactionTypeEnum;
import com.mindecho.module.billing.mapper.AiUsageRecordMapper;
import com.mindecho.module.billing.mapper.PointTransactionMapper;
import com.mindecho.module.billing.mapper.UserPointAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 积分查询服务（只读，用于账单展示）
 */
@Service
@RequiredArgsConstructor
public class BillingQueryService {

    private final UserPointAccountMapper accountMapper;
    private final PointTransactionMapper transactionMapper;
    private final AiUsageRecordMapper usageRecordMapper;
    private final PointAccountService pointAccountService;

    /**
     * 查询用户积分账户信息
     */
    public PointAccountDTO getAccountInfo(Long userId) {
        UserPointAccount account = pointAccountService.getAccount(userId);
        return PointAccountDTO.builder()
                .balance(account.getBalance())
                .frozenBalance(account.getFrozenBalance())
                .totalRecharge(account.getTotalRecharge())
                .totalConsume(account.getTotalConsume())
                .totalRefund(account.getTotalRefund())
                .totalGift(account.getTotalGift())
                .build();
    }

    /**
     * 分页查询积分流水（全部类型）
     */
    public IPage<TransactionRecordDTO> getTransactionList(Long userId, Integer page, Integer size) {
        Page<PointTransaction> pageParam = new Page<>(page, size);
        IPage<PointTransaction> txPage = transactionMapper.selectPage(pageParam,
                new LambdaQueryWrapper<PointTransaction>()
                        .eq(PointTransaction::getUserId, userId)
                        .orderByDesc(PointTransaction::getCreatedTime)
        );
        return txPage.convert(this::toTransactionDTO);
    }

    /**
     * 分页查询充值流水
     */
    public IPage<TransactionRecordDTO> getRechargeList(Long userId, Integer page, Integer size) {
        Page<PointTransaction> pageParam = new Page<>(page, size);
        IPage<PointTransaction> txPage = transactionMapper.selectPage(pageParam,
                new LambdaQueryWrapper<PointTransaction>()
                        .eq(PointTransaction::getUserId, userId)
                        .eq(PointTransaction::getType, TransactionTypeEnum.RECHARGE.getCode())
                        .orderByDesc(PointTransaction::getCreatedTime)
        );
        return txPage.convert(this::toTransactionDTO);
    }

    /**
     * 分页查询消费流水（CONSUME 类型）
     */
    public IPage<TransactionRecordDTO> getConsumeList(Long userId, Integer page, Integer size) {
        Page<PointTransaction> pageParam = new Page<>(page, size);
        IPage<PointTransaction> txPage = transactionMapper.selectPage(pageParam,
                new LambdaQueryWrapper<PointTransaction>()
                        .eq(PointTransaction::getUserId, userId)
                        .eq(PointTransaction::getType, TransactionTypeEnum.CONSUME.getCode())
                        .orderByDesc(PointTransaction::getCreatedTime)
        );
        return txPage.convert(this::toTransactionDTO);
    }

    /**
     * 分页查询 AI 使用量记录（含消费详情）
     */
    public IPage<UsageRecordDTO> getUsageList(Long userId, Integer page, Integer size) {
        Page<AiUsageRecord> pageParam = new Page<>(page, size);
        IPage<AiUsageRecord> usagePage = usageRecordMapper.selectPage(pageParam,
                new LambdaQueryWrapper<AiUsageRecord>()
                        .eq(AiUsageRecord::getUserId, userId)
                        .orderByDesc(AiUsageRecord::getCreatedTime)
        );
        return usagePage.convert(this::toUsageDTO);
    }

    // ─────────────────────── 转换方法 ───────────────────────

    private TransactionRecordDTO toTransactionDTO(PointTransaction tx) {
        return TransactionRecordDTO.builder()
                .id(tx.getId())
                .transactionNo(tx.getTransactionNo())
                .amount(tx.getAmount())
                .type(tx.getType())
                .typeDesc(resolveTypeDesc(tx.getType()))
                .beforeBalance(tx.getBeforeBalance())
                .afterBalance(tx.getAfterBalance())
                .remark(tx.getRemark())
                .status(tx.getStatus())
                .createdTime(tx.getCreatedTime())
                .build();
    }

    private UsageRecordDTO toUsageDTO(AiUsageRecord record) {
        return UsageRecordDTO.builder()
                .id(record.getId())
                .businessType(record.getBusinessType())
                .businessTypeDesc(resolveBusinessTypeDesc(record.getBusinessType()))
                .modelName(record.getModelName())
                .promptTokens(record.getPromptTokens())
                .completionTokens(record.getCompletionTokens())
                .totalTokens(record.getTotalTokens())
                .contextTokens(record.getContextTokens())
                .estimatedPoints(record.getEstimatedPoints())
                .actualPoints(record.getActualPoints())
                .status(record.getStatus())
                .createdTime(record.getCreatedTime())
                .build();
    }

    private String resolveTypeDesc(String type) {
        if (type == null) return "未知";
        return switch (type) {
            case "RECHARGE" -> "充值";
            case "PRE_DEDUCT" -> "预扣";
            case "CONSUME" -> "消费";
            case "REFUND" -> "退回";
            case "SYSTEM_GIFT" -> "赠送";
            case "ADMIN_ADJUST" -> "后台调整";
            default -> type;
        };
    }

    private String resolveBusinessTypeDesc(String businessType) {
        if (businessType == null) return "未知";
        return switch (businessType) {
            case "CHAT" -> "聊天";
            case "ASTROLOGY_NATAL" -> "本命盘解读";
            case "ASTROLOGY_SYNASTRY" -> "和盘解读";
            case "ASTROLOGY_TRANSIT" -> "流运解读";
            case "VOICE" -> "语音聊天";
            case "IMAGE" -> "图片生成";
            default -> businessType;
        };
    }
}

