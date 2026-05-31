package com.mindecho.module.astrology.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindecho.common.util.UserContext;
import com.mindecho.module.astrology.dto.*;
import com.mindecho.module.astrology.service.AstrologyAiService;
import com.mindecho.module.astrology.service.AstrologyGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 占星工具集（与 astrology Skill 绑定）
 *
 * <p>通过 Spring AI {@code @Tool} 注解暴露为工具，配合 {@code SkillPromptAugmentAdvisor}
 * 实现渐进式披露：仅当用户询问占星相关问题、模型调用 {@code read_skill("astrology")} 激活
 * 技能后，这些工具才会注入到当次请求的工具列表中。
 *
 * <p>工具调用链：
 * <pre>
 * 用户问题（涉及星盘）
 *   → 模型发现 astrology skill → 调用 read_skill("astrology")
 *   → skill 激活，工具列表注入
 *   → 模型调用 astrology_calculate_natal / astrology_interpret_natal 等
 *   → 返回结构化结果
 * </pre>
 *
 * <p>注意：ObjectMapper 通过构造函数注入并指定 {@code @Qualifier("webObjectMapper")}，
 * 不可使用 Lombok {@code @RequiredArgsConstructor}，因为 Lombok 生成的构造函数不携带
 * 字段上的 {@code @Qualifier} 注解，会导致 Spring 注入错误的 Bean。
 */
@Slf4j
@Component
public class AstrologyTools {

    private final AstrologyGatewayService gatewayService;
    private final AstrologyAiService aiService;
    private final ObjectMapper objectMapper;

    public AstrologyTools(AstrologyGatewayService gatewayService,
                          AstrologyAiService aiService,
                          @Qualifier("webObjectMapper") ObjectMapper objectMapper) {
        this.gatewayService = gatewayService;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    // ─────────────────────── 本命盘 ───────────────────────────────────────────

    /**
     * 计算本命盘
     *
     * <p>出生信息从用户 Profile 读取，首次调用转发 Python 服务计算，结果永久缓存（Redis），后续直接返回缓存。
     */
    @Tool(name = "astrology_calculate_natal",
          description = "计算用户本命星盘数据（Natal Chart）。" +
                        "出生信息自动从用户档案中读取，无需额外提供。" +
                        "返回行星位置、上升点、宫位等完整星盘 JSON 数据，用于后续 AI 解读。")
    public String calculateNatal() {
        try {
            UUID userId = UserContext.getUserId();
            log.info("[AstrologyTool] calculateNatal: userId={}", userId);

            NatalChartResponseDTO result = gatewayService.getNatalChart(userId);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("[AstrologyTool] calculateNatal failed", e);
            return "{\"error\": \"本命盘计算失败：" + e.getMessage() + "\"}";
        }
    }

    /**
     * 对本命盘进行 AI 解读
     *
     * <p>星盘数据自动从用户 Profile 读取，通常在 {@link #calculateNatal} 之后调用。
     */
    @Tool(name = "astrology_interpret_natal",
          description = "对本命盘（Natal Chart）进行 AI 占星解读。" +
                        "星盘数据自动从用户档案读取，无需传入 chart JSON。" +
                        "focus 参数控制解读重点：personality（性格）、career（事业）、emotion（情感）、growth（成长），不填则全面解读。")
    public String interpretNatal(
            @ToolParam(description = "解读焦点：personality（性格/潜能）、career（事业/天赋）、emotion（情感模式）、growth（成长方向），留空则全面解读") String focus,
            @ToolParam(description = "解读语气：gentle（温柔）、rational（理性）、deep（深度心理），默认 gentle") String tone) {
        try {
            UUID userId = UserContext.getUserId();
            log.info("[AstrologyTool] interpretNatal: userId={}, focus={}", userId, focus);

            AstrologyInterpretRequestDTO request = new AstrologyInterpretRequestDTO();
            request.setFocus(focus);
            request.setTone(tone != null && !tone.isBlank() ? tone : "gentle");

            AstrologyInterpretResponseDTO result = aiService.interpretNatal(userId, request);
            return result.getInterpretation();
        } catch (Exception e) {
            log.error("[AstrologyTool] interpretNatal failed", e);
            return "本命盘解读失败：" + e.getMessage();
        }
    }

    // ─────────────────────── 合盘 / 和盘 ─────────────────────────────────────

    /**
     * 计算两人合盘
     *
     * <p>自己的出生信息从用户 Profile 读取；对方信息从 user 表已保存的 synastry_partner_* 字段读取。
     * 若需要更新对方信息，请通过 /api/auth/synastry-partner 接口设置后再调用本工具。
     * 含 30 天缓存。
     */
    @Tool(name = "astrology_calculate_synastry",
          description = "计算两人合盘（Synastry Chart），分析两人星盘的相互关系和兼容性。" +
                        "双方出生信息均自动从用户档案中读取（自己的出生信息 + 上次设置的合盘对方信息），无需额外提供。" +
                        "若用户想分析不同对象，请先通过个人设置更新合盘对方信息。含 30 天缓存。")
    public String calculateSynastry() {
        try {
            UUID userId = UserContext.getUserId();
            log.info("[AstrologyTool] calculateSynastry: userId={}", userId);

            SynastryResponseDTO result = gatewayService.getSynastryChart(userId);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("[AstrologyTool] calculateSynastry failed", e);
            return "{\"error\": \"合盘计算失败：" + e.getMessage() + "\"}";
        }
    }

    /**
     * 对合盘进行 AI 解读
     *
     * <p>合盘数据自动从用户 Profile 读取。
     */
    @Tool(name = "astrology_interpret_synastry",
          description = "对合盘（Synastry Chart）进行 AI 占星解读，分析两人的感情缘分和关系模式。" +
                        "合盘数据及对方信息自动从用户档案读取，无需传入 chart JSON。" +
                        "focus 参数：compatibility（兼容性）、dynamic（关系动力）、challenge（关系挑战）、growth（共同成长）。")
    public String interpretSynastry(
            @ToolParam(description = "关系类型：romantic（恋人）、friend（朋友）、family（家人）、colleague（同事），留空默认 romantic") String relationshipType,
            @ToolParam(description = "解读焦点：compatibility（兼容性）、dynamic（关系动力）、challenge（关系挑战）、growth（共同成长），留空则全面解读") String focus,
            @ToolParam(description = "解读语气：gentle（温柔）、rational（理性）、deep（深度），默认 gentle") String tone) {
        try {
            UUID userId = UserContext.getUserId();
            log.info("[AstrologyTool] interpretSynastry: userId={}, focus={}", userId, focus);

            SynastryInterpretRequestDTO request = new SynastryInterpretRequestDTO();
            request.setRelationshipType(relationshipType != null && !relationshipType.isBlank() ? relationshipType : "romantic");
            request.setFocus(focus);
            request.setTone(tone != null && !tone.isBlank() ? tone : "gentle");

            AstrologyInterpretResponseDTO result = aiService.interpretSynastry(userId, request);
            return result.getInterpretation();
        } catch (Exception e) {
            log.error("[AstrologyTool] interpretSynastry failed", e);
            return "合盘解读失败：" + e.getMessage();
        }
    }

    // ─────────────────────── 流运 ─────────────────────────────────────────────

    /**
     * 计算流运
     *
     * <p>出生信息从用户 Profile 读取，只需传查询日期。含 24 小时缓存。
     */
    @Tool(name = "astrology_calculate_transit",
          description = "计算指定日期的流运（Transit），分析当前行星过境对本命盘的影响。" +
                        "出生信息自动从用户档案读取，只需提供目标查询日期（可选）。含 24 小时缓存。")
    public String calculateTransit(
            @ToolParam(description = "目标查询日期，格式 yyyy-MM-dd，如 2026-05-29。留空则使用今天") String targetDate) {
        try {
            UUID userId = UserContext.getUserId();
            String date = (targetDate != null && !targetDate.isBlank())
                    ? targetDate
                    : java.time.LocalDate.now().toString();
            log.info("[AstrologyTool] calculateTransit: userId={}, date={}", userId, date);

            TransitRequestDTO request = new TransitRequestDTO();
            request.setTargetDate(date);

            TransitResponseDTO result = gatewayService.getTransitChart(userId, request);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("[AstrologyTool] calculateTransit failed", e);
            return "{\"error\": \"流运计算失败：" + e.getMessage() + "\"}";
        }
    }

    /**
     * 对流运进行 AI 解读
     *
     * <p>流运数据自动从用户 Profile 读取。
     */
    @Tool(name = "astrology_interpret_transit",
          description = "对流运（Transit）进行 AI 占星解读，分析近期行星影响和运势变化趋势。" +
                        "流运数据自动从用户档案读取，无需传入 chart JSON。" +
                        "focus 参数：current（当下状态）、love（情感变化）、career（事业机遇）、advice（近期建议）。")
    public String interpretTransit(
            @ToolParam(description = "解读焦点：current（当下状态）、love（情感变化）、career（事业机遇）、advice（近期建议），留空则全面解读") String focus,
            @ToolParam(description = "解读语气：gentle（温柔）、rational（理性）、deep（深度），默认 gentle") String tone) {
        try {
            UUID userId = UserContext.getUserId();
            log.info("[AstrologyTool] interpretTransit: userId={}, focus={}", userId, focus);

            TransitInterpretRequestDTO request = new TransitInterpretRequestDTO();
            request.setFocus(focus);
            request.setTone(tone != null && !tone.isBlank() ? tone : "gentle");

            AstrologyInterpretResponseDTO result = aiService.interpretTransit(userId, request);
            return result.getInterpretation();
        } catch (Exception e) {
            log.error("[AstrologyTool] interpretTransit failed", e);
            return "流运解读失败：" + e.getMessage();
        }
    }
}

