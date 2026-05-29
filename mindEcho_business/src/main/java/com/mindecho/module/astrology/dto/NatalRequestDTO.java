package com.mindecho.module.astrology.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单星盘（本命盘）计算请求
 *
 * POST /api/astrology/natal
 */
@Data
public class NatalRequestDTO {

    /** 出生信息（必填） */
    @Valid
    @NotNull(message = "出生信息不能为空")
    private BirthInfoDTO birthInfo;

    /**
     * 是否保存到当前用户档案（true = 持久化到数据库，后续解读时直接使用缓存星盘）
     * 默认 true
     */
    private boolean saveToProfile = true;
}

