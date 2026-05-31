package com.mindecho.module.astrology.dto;

import lombok.Data;

/**
 * AI 占星解读请求（适用于单盘解读接口）
 *
 * <p>新版架构：chart 数据由后端从 user_astrology 表自动读取，前端无需传入。
 *
 * POST /api/astrology/natal/interpret
 */
@Data
public class AstrologyInterpretRequestDTO {

    /**
     * 解读焦点（控制 AI 重点方向）
     *
     * 单盘可选值：
     *   personality（性格/潜能）、career（事业/天赋）、
     *   emotion（情感模式）、growth（成长方向）
     *
     * 默认为 null = 全面解读
     */
    private String focus;

    /**
     * 解读语气（覆盖默认人格）
     * gentle（温柔）/ rational（理性）/ deep（深度心理）
     * 默认 gentle
     */
    private String tone = "gentle";

    /**
     * 解读类型标识（由 Controller 层注入，Service 用于区分 Memory 检索策略）
     * NATAL / SYNASTRY / TRANSIT
     */
    private String interpretType;

    /**
     * 额外上下文（由 Controller 层根据接口类型注入，对单盘解读可为 null）
     */
    private String extraContext;
}

