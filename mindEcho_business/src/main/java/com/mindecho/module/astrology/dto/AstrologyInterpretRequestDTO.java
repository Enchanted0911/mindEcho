package com.mindecho.module.astrology.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 占星解读请求（通用，适用于单盘/和盘/流运三类解读接口）
 *
 * POST /api/astrology/natal/interpret
 * POST /api/astrology/synastry/interpret
 * POST /api/astrology/transit/interpret
 */
@Data
public class AstrologyInterpretRequestDTO {

    /**
     * 星盘原始数据（来自 natal / synastry / transit 接口的响应中的 chart 字段）
     * 直接透传给 RAG + Prompt，不做额外解析
     */
    @NotNull(message = "星盘数据不能为空")
    private JsonNode chart;

    /**
     * 解读焦点（控制 AI 重点方向）
     *
     * 单盘可选值：
     *   personality（性格/潜能）、career（事业/天赋）、
     *   emotion（情感模式）、growth（成长方向）
     *
     * 和盘可选值：
     *   compatibility（兼容性）、dynamic（关系动力）、
     *   challenge（关系挑战）、growth（共同成长）
     *
     * 流运可选值：
     *   current（当下状态）、love（情感变化）、
     *   career（事业机遇）、advice（近期建议）
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
     * 流运/和盘场景下的额外上下文（如对方名字、查询日期等）
     * 由 Controller 层根据接口类型注入，对单盘解读可为 null
     */
    private String extraContext;
}

