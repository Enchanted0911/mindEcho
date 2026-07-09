package com.mindecho.module.astrology.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindecho.module.astrology.entity.UserAstrology;
import com.mindecho.module.astrology.mapper.UserAstrologyMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 用户星盘背景上下文构建服务
 *
 * <p>从 {@code user_astrology} 表读取用户的星盘信息，构建可直接注入主聊天 System Prompt 的
 * 星盘背景文本。主聊天模型通过该上下文感知用户的占星基础信息，提供更有针对性的情绪陪伴。
 *
 * <p>注入策略：
 * <ul>
 *   <li>仅注入出生信息 + 本命盘核心摘要，轻量不冗长</li>
 *   <li>核心摘要从 {@code natal_chart_data} 的 {@code summary} 节点提取太阳/月亮/上升星座</li>
 *   <li>最近一次星盘解读摘要（natal_interpretation 截取前 150 字）作为补充</li>
 *   <li>若用户无星盘数据，返回 null（不注入，不影响正常聊天）</li>
 * </ul>
 */
@Slf4j
@Service
public class UserAstrologyContextBuilder {

    private final UserAstrologyMapper userAstrologyMapper;
    private final ObjectMapper objectMapper;

    public UserAstrologyContextBuilder(UserAstrologyMapper userAstrologyMapper,
                                        @Qualifier("webObjectMapper") ObjectMapper objectMapper) {
        this.userAstrologyMapper = userAstrologyMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 为主聊天构建用户星盘背景上下文文本
     *
     * <p>该方法从 user_astrology 表查询用户星盘记录，提取关键信息构建成
     * 可注入到 System Prompt 的文本片段。若用户尚未填写出生信息或没有星盘数据，
     * 则返回 null，不向 Prompt 中注入任何内容。
     *
     * @param userId 用户 ID
     * @return 星盘背景上下文文本（可能为 null）
     */
    public String buildAstrologyContext(UUID userId) {
        try {
            UserAstrology astrology = userAstrologyMapper.selectOne(
                    new LambdaQueryWrapper<UserAstrology>()
                            .eq(UserAstrology::getUserId, userId)
                            .eq(UserAstrology::getDeleted, 0)
            );

            if (astrology == null) {
                return null;
            }

            // 出生信息是基础，城市和时间都为空时没有任何有意义的星盘背景，直接返回 null
            // 只要其中一个有值（如只有城市但没有时间），仍可展示基础背景信息
            if (!StringUtils.hasText(astrology.getBirthCity())
                    && !StringUtils.hasText(astrology.getBirthTime())) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("【用户星盘背景】\n");

            // 出生信息
            if (StringUtils.hasText(astrology.getBirthCity())
                    || StringUtils.hasText(astrology.getBirthTime())) {
                sb.append("出生信息：");
                if (StringUtils.hasText(astrology.getBirthCity())) {
                    sb.append("出生于").append(astrology.getBirthCity());
                }
                if (StringUtils.hasText(astrology.getBirthTime())) {
                    sb.append("，出生时间 ").append(astrology.getBirthTime());
                }
                sb.append("\n");
            }

            // 本命盘核心摘要：从 natal_chart_data 的 summary 节点提取太阳/月亮/上升星座
            if (StringUtils.hasText(astrology.getNatalChartData())) {
                String summaryDesc = extractNatalSummaryDescFromChartData(astrology.getNatalChartData());
                if (StringUtils.hasText(summaryDesc)) {
                    sb.append("星盘核心：").append(summaryDesc).append("\n");
                }
            }

            // 最近本命盘解读摘要（截取前 150 字，作为上下文参考）
            if (StringUtils.hasText(astrology.getNatalInterpretation())) {
                String snippet = astrology.getNatalInterpretation().length() > 150
                        ? astrology.getNatalInterpretation().substring(0, 150) + "…"
                        : astrology.getNatalInterpretation();
                sb.append("最近解读：").append(snippet).append("\n");
            }

            // 和盘对方信息（若有，体现用户目前在乎的关系）
            if (StringUtils.hasText(astrology.getSynastryPartnerName())) {
                sb.append("关注的关系：与").append(astrology.getSynastryPartnerName())
                        .append("的星盘关系已分析\n");
            }

            String result = sb.toString().trim();
            if (result.equals("【用户星盘背景】")) {
                // 只有标题，没有任何内容，不注入
                return null;
            }

            log.debug("UserAstrologyContextBuilder: built astrology context for userId={}, length={}",
                    userId, result.length());
            return result;

        } catch (Exception e) {
            log.warn("UserAstrologyContextBuilder: failed to build astrology context for userId={}: {}",
                    userId, e.getMessage());
            return null;
        }
    }

    /**
     * 从 {@code natal_chart_data} JSON 中提取人类可读的星座描述
     *
     * <p>{@code natal_chart_data} 中保存的是完整 {@link com.mindecho.module.astrology.dto.NatalChartResponseDTO}，
     * 其 {@code summary} 节点格式示例：
     * <pre>
     * {
     *   "sun":       { "sign": "Libra", ... },
     *   "moon":      { "sign": "Sagittarius", ... },
     *   "ascendant": { "sign": "Aries", ... },
     *   ...
     * }
     * </pre>
     * 提取后输出：「太阳Libra 月亮Sagittarius 上升Aries」
     *
     * @param natalChartDataJson {@code user_astrology.natal_chart_data} 字段值
     * @return 人类可读描述，解析失败返回 null
     */
    private String extractNatalSummaryDescFromChartData(String natalChartDataJson) {
        try {
            JsonNode root = objectMapper.readTree(natalChartDataJson);
            JsonNode summary = root.path("summary");
            if (summary.isMissingNode() || summary.isNull()) {
                return null;
            }

            StringBuilder desc = new StringBuilder();
            appendNestedSignField(desc, summary, "sun",       "太阳");
            appendNestedSignField(desc, summary, "moon",      "月亮");
            appendNestedSignField(desc, summary, "ascendant", "上升");

            return desc.toString().trim();
        } catch (Exception e) {
            log.debug("Failed to extract summary from natal_chart_data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从嵌套对象节点中读取 {@code sign} 字段并追加到描述中
     *
     * @param sb     目标 StringBuilder
     * @param parent 父节点（即 summary 节点）
     * @param key    子节点 key（如 "sun"、"moon"、"ascendant"）
     * @param label  中文标签
     */
    private void appendNestedSignField(StringBuilder sb, JsonNode parent, String key, String label) {
        JsonNode child = parent.path(key);
        if (child.isMissingNode() || child.isNull()) return;
        String sign = child.path("sign").asText(null);
        if (StringUtils.hasText(sign) && !"null".equals(sign)) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(label).append(sign);
        }
    }
}

