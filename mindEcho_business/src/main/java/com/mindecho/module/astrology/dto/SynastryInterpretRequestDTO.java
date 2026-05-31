package com.mindecho.module.astrology.dto;

import lombok.Data;

/**
 * 和盘 AI 解读请求
 *
 * <p>chart 数据由后端从 user 表读取，前端只需传关系类型和解读焦点。
 * 前置条件：user 表中已有 synastry_chart_data（即计算过和盘）。
 *
 * POST /api/astrology/synastry/interpret
 */
@Data
public class SynastryInterpretRequestDTO {

    /**
     * 关系类型（用于 Prompt 调整风格）
     * romantic / family / friendship / colleague
     * 默认 romantic
     */
    private String relationshipType = "romantic";

    /**
     * 解读焦点（控制 AI 重点方向）
     * compatibility / dynamic / challenge / growth
     * 默认 null = 全面解读
     */
    private String focus;

    /**
     * 解读语气
     * gentle / rational / deep
     * 默认 gentle
     */
    private String tone = "gentle";
}

