package com.mindecho.module.astrology.dto;

import lombok.Data;

/**
 * 流运 AI 解读请求
 *
 * <p>chart 数据由后端从 user 表读取，前端只需传时间窗口和解读焦点。
 * 前置条件：user 表中已有流运星盘数据（synastry_chart_summary 字段中的流运缓存，即计算过流运）。
 *
 * POST /api/astrology/transit/interpret
 */
@Data
public class TransitInterpretRequestDTO {

    /**
     * 流运窗口天数（查询未来 N 天的重要流运事件），默认 30
     * 与上次计算流运时的 windowDays 对应，用于 extraContext 提示
     */
    private Integer windowDays = 30;

    /**
     * 解读焦点（控制 AI 重点方向）
     * current / love / career / advice
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

