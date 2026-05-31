package com.mindecho.module.astrology.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 和盘（合盘）计算请求
 *
 * <p>自己的出生信息由后端从 user 表读取，前端只需传对方的出生信息和名字。
 * 若用户未设置出生信息，后端返回 7001 错误，前端应引导用户先设置出生信息。
 *
 * POST /api/astrology/synastry
 */
@Data
public class SynastryRequestDTO {

    /**
     * 对方出生信息（自己的出生信息由后端从 user 表读取）
     */
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

