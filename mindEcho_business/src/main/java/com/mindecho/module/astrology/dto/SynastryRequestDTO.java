package com.mindecho.module.astrology.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 和盘（合盘）计算请求
 *
 * POST /api/astrology/synastry
 */
@Data
public class SynastryRequestDTO {

    /** 本人出生信息 */
    @Valid
    @NotNull(message = "本人出生信息不能为空")
    private BirthInfoDTO selfBirthInfo;

    /** 对方出生信息 */
    @Valid
    @NotNull(message = "对方出生信息不能为空")
    private BirthInfoDTO partnerBirthInfo;

    /** 对方昵称（用于 Memory 存储，如 "前男友小明"） */
    @NotBlank(message = "对方昵称不能为空")
    private String partnerName;

    /**
     * 关系类型（用于 Prompt 调整风格和 Memory 标签）
     * romantic / family / friendship / colleague
     */
    private String relationshipType = "romantic";
}

