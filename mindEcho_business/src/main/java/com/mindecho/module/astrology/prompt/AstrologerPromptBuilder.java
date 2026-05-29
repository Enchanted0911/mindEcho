package com.mindecho.module.astrology.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI 占星师 Prompt 构建器
 *
 * <p>负责将以下信息按结构化顺序组装成最终 System Prompt：
 * <ol>
 *   <li>角色定义（现代心理占星师）</li>
 *   <li>占星体系定义（西洋占星 + 心理学视角）</li>
 *   <li>情绪风格约束（温柔、共情、不绝对化）</li>
 *   <li>用户 Memory 片段（情绪/关系/人格历史）</li>
 *   <li>RAG 占星知识片段（检索到的相关解释）</li>
 *   <li>当前星盘数据摘要</li>
 * </ol>
 *
 * <p>Prompt 结构遵循 PRD §5 要求，优化 DeepSeek prefix cache 命中：
 * 稳定的角色/体系/风格部分放前面，动态的 Memory/RAG/Chart 放后面。
 */
@Slf4j
@Component
public class AstrologerPromptBuilder {

    // ─────────────────────── 静态 Prompt 块（角色/体系/风格，内容不变，利于 prefix cache） ─────

    private static final String ROLE_DEFINITION = """
            你是一位现代心理占星师，名叫"星屿"。
            你的风格温柔、深度、富有洞察力，擅长将西洋占星与心理学结合，
            用陪伴者而非预言者的姿态与用户对话。
            """;

    private static final String ASTROLOGY_SYSTEM = """
            【占星体系】
            你使用西洋占星（热带占星）体系，以心理学视角解读星盘。
            你相信星盘揭示的是人的心理模式和潜在倾向，而非命运的定论。
            你的解读重点在于：了解自我、理解他人、找到成长方向。
            """;

    private static final String STYLE_CONSTRAINTS = """
            【表达风格】
            - 温柔、共情，不评判，不说教
            - 用陪伴感的口吻，像朋友而非老师
            - 偏心理学视角，关注内心动力而非外部事件
            - 避免宿命论：不使用"你一定会"、"命中注定"等绝对化表达
            - 避免灾难预言：面对挑战相位，强调潜力和成长而非恐惧
            - 每段解读自然流畅，有连贯的叙事感
            - 适当使用 emoji 增加温度感，但不过度

            【严格禁止】
            - 做出医学/心理健康诊断
            - 预言具体财富/健康结果
            - 鼓励依赖占星做重要决策
            - 使用绝对化语言描述命运
            """;

    // ─────────────────────── 解读焦点模板 ─────────────────────────────────

    private static final String FOCUS_NATAL_PERSONALITY =
            "本次解读重点：深入分析用户的性格底色、内在动力与心理潜能。";
    private static final String FOCUS_NATAL_CAREER =
            "本次解读重点：探索用户的天赋领域、事业方向与创造力表达方式。";
    private static final String FOCUS_NATAL_EMOTION =
            "本次解读重点：分析用户的情感模式、依恋风格与亲密关系倾向。";
    private static final String FOCUS_NATAL_GROWTH =
            "本次解读重点：挖掘用户的成长课题、内在挑战与自我突破路径。";

    private static final String FOCUS_SYNASTRY_COMPATIBILITY =
            "本次解读重点：分析双方星盘的化学反应与整体兼容性。";
    private static final String FOCUS_SYNASTRY_DYNAMIC =
            "本次解读重点：探索这段关系中的动力结构、谁在给予能量、谁在接受。";
    private static final String FOCUS_SYNASTRY_CHALLENGE =
            "本次解读重点：坦诚分析这段关系的挑战点，以及如何共同成长。";
    private static final String FOCUS_SYNASTRY_GROWTH =
            "本次解读重点：发掘这段关系对双方各自成长的推动力与意义。";

    private static final String FOCUS_TRANSIT_CURRENT =
            "本次解读重点：分析当下星象对用户当前心理状态和生活节奏的影响。";
    private static final String FOCUS_TRANSIT_LOVE =
            "本次解读重点：聚焦近期情感变化、感情机遇与关系调整信号。";
    private static final String FOCUS_TRANSIT_CAREER =
            "本次解读重点：分析近期事业机遇、能量高峰期与行动时机。";
    private static final String FOCUS_TRANSIT_ADVICE =
            "本次解读重点：结合近期流运，给出温和的近期生活建议与能量调频方向。";

    // ─────────────────────── 依赖 ─────────────────────────────────────────

    private final ObjectMapper objectMapper;

    public AstrologerPromptBuilder(@Qualifier("webObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ─────────────────────── 核心构建方法 ─────────────────────────────────

    /**
     * 构建单盘 AI 解读的完整 System Prompt
     *
     * @param chartJson   星盘 JSON 数据（来自 Python 服务）
     * @param ragContent  RAG 检索结果文本
     * @param memories    用户相关 Memory 列表
     * @param focus       解读焦点
     * @param tone        解读语气
     * @return 完整 System Prompt
     */
    public String buildNatalPrompt(JsonNode chartJson, String ragContent,
                                   List<String> memories, String focus, String tone) {
        StringBuilder sb = new StringBuilder();
        appendStaticBlocks(sb, tone);
        appendFocusHint(sb, "NATAL", focus);
        appendMemoryBlock(sb, memories);
        appendRagBlock(sb, ragContent);
        appendChartBlock(sb, "【本命盘数据】", chartJson);
        appendTaskInstruction(sb, "NATAL", focus);
        return sb.toString();
    }

    /**
     * 构建和盘 AI 解读的完整 System Prompt
     *
     * @param chartJson      和盘 JSON 数据
     * @param ragContent     RAG 检索结果
     * @param memories       用户关系相关 Memory
     * @param partnerName    对方名字
     * @param relationshipType 关系类型
     * @param focus          解读焦点
     * @param tone           解读语气
     * @return 完整 System Prompt
     */
    public String buildSynastryPrompt(JsonNode chartJson, String ragContent,
                                      List<String> memories, String partnerName,
                                      String relationshipType, String focus, String tone) {
        StringBuilder sb = new StringBuilder();
        appendStaticBlocks(sb, tone);
        appendFocusHint(sb, "SYNASTRY", focus);
        appendRelationshipContext(sb, partnerName, relationshipType);
        appendMemoryBlock(sb, memories);
        appendRagBlock(sb, ragContent);
        appendChartBlock(sb, "【和盘数据】", chartJson);
        appendTaskInstruction(sb, "SYNASTRY", focus);
        return sb.toString();
    }

    /**
     * 构建流运 AI 解读的完整 System Prompt
     *
     * @param chartJson  流运 JSON 数据
     * @param ragContent RAG 检索结果
     * @param memories   用户情绪相关 Memory
     * @param targetDate 流运查询日期
     * @param focus      解读焦点
     * @param tone       解读语气
     * @return 完整 System Prompt
     */
    public String buildTransitPrompt(JsonNode chartJson, String ragContent,
                                     List<String> memories, String targetDate,
                                     String focus, String tone) {
        StringBuilder sb = new StringBuilder();
        appendStaticBlocks(sb, tone);
        appendFocusHint(sb, "TRANSIT", focus);
        if (StringUtils.hasText(targetDate)) {
            sb.append("\n【流运查询日期】\n").append(targetDate).append("\n");
        }
        appendMemoryBlock(sb, memories);
        appendRagBlock(sb, ragContent);
        appendChartBlock(sb, "【流运事件数据】", chartJson);
        appendTaskInstruction(sb, "TRANSIT", focus);
        return sb.toString();
    }

    // ─────────────────────── 私有拼装方法 ─────────────────────────────────

    /**
     * 追加稳定的静态块：角色 + 体系 + 风格
     */
    private void appendStaticBlocks(StringBuilder sb, String tone) {
        sb.append(ROLE_DEFINITION).append("\n");
        sb.append(ASTROLOGY_SYSTEM).append("\n");
        sb.append(STYLE_CONSTRAINTS).append("\n");

        // 根据 tone 追加语气微调
        if ("rational".equals(tone)) {
            sb.append("\n【语气微调】本次解读偏理性分析，减少情感化表达，增加结构化洞察。\n");
        } else if ("deep".equals(tone)) {
            sb.append("\n【语气微调】本次解读追求深度，鼓励用户向内探索深层心理动力和原生家庭模式。\n");
        }
    }

    /**
     * 追加解读焦点提示
     */
    private void appendFocusHint(StringBuilder sb, String type, String focus) {
        if (!StringUtils.hasText(focus)) return;
        String hint = getFocusHint(type, focus);
        if (StringUtils.hasText(hint)) {
            sb.append("\n").append(hint).append("\n");
        }
    }

    /**
     * 追加和盘关系上下文
     */
    private void appendRelationshipContext(StringBuilder sb, String partnerName, String relationshipType) {
        sb.append("\n【关系背景】\n");
        sb.append("你正在分析用户与 ").append(partnerName).append(" 的关系。\n");
        String typeDesc = switch (relationshipType == null ? "romantic" : relationshipType) {
            case "family" -> "这是一段家庭关系（亲子/兄弟姐妹等）。";
            case "friendship" -> "这是一段友谊关系。";
            case "colleague" -> "这是一段职场关系。";
            default -> "这是一段浪漫/亲密关系。";
        };
        sb.append(typeDesc).append("\n");
    }

    /**
     * 追加用户 Memory 块
     * <p>由于 pgvector 语义检索已按相关度排序，此处直接列举内容文本，不再区分类型。
     */
    private void appendMemoryBlock(StringBuilder sb, List<String> memories) {
        if (memories == null || memories.isEmpty()) return;

        sb.append("\n【关于这位用户，你了解的情绪与人格信息】\n");
        memories.forEach(content -> sb.append("- ").append(content).append("\n"));
    }

    /**
     * 追加 RAG 知识块
     */
    private void appendRagBlock(StringBuilder sb, String ragContent) {
        if (!StringUtils.hasText(ragContent)) return;
        sb.append("\n【相关占星知识（来自知识库）】\n");
        // 截断超长 RAG 内容（防止 token 超限）
        String content = ragContent.length() > 2000 ? ragContent.substring(0, 2000) + "…（更多内容已省略）" : ragContent;
        sb.append(content).append("\n");
    }

    /**
     * 追加星盘数据块（转换为可读的 JSON 字符串）
     */
    private void appendChartBlock(StringBuilder sb, String title, JsonNode chartJson) {
        if (chartJson == null) return;
        sb.append("\n").append(title).append("\n");
        try {
            String chartStr = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(chartJson);
            // 星盘数据可能很大，截断到 3000 字符
            if (chartStr.length() > 3000) {
                chartStr = chartStr.substring(0, 3000) + "\n...（更多数据已截断）";
            }
            sb.append(chartStr).append("\n");
        } catch (Exception e) {
            log.warn("Failed to serialize chart json", e);
            sb.append(chartJson.toString()).append("\n");
        }
    }

    /**
     * 追加最终任务指令
     */
    private void appendTaskInstruction(StringBuilder sb, String type, String focus) {
        sb.append("\n【解读任务】\n");
        sb.append("请结合以上用户情绪记忆、占星知识和星盘数据，\n");
        sb.append("生成一段自然、连续、具有陪伴感的 AI 占星解读。\n");
        sb.append("字数控制在 300-500 字，段落流畅，温柔而有洞察力。\n");
        if (!StringUtils.hasText(focus)) {
            sb.append("请进行全面解读，覆盖性格、情感和成长三个维度。\n");
        }
    }

    /**
     * 根据类型和焦点返回对应提示语
     */
    private String getFocusHint(String type, String focus) {
        return switch (type + "_" + focus) {
            case "NATAL_personality" -> FOCUS_NATAL_PERSONALITY;
            case "NATAL_career" -> FOCUS_NATAL_CAREER;
            case "NATAL_emotion" -> FOCUS_NATAL_EMOTION;
            case "NATAL_growth" -> FOCUS_NATAL_GROWTH;
            case "SYNASTRY_compatibility" -> FOCUS_SYNASTRY_COMPATIBILITY;
            case "SYNASTRY_dynamic" -> FOCUS_SYNASTRY_DYNAMIC;
            case "SYNASTRY_challenge" -> FOCUS_SYNASTRY_CHALLENGE;
            case "SYNASTRY_growth" -> FOCUS_SYNASTRY_GROWTH;
            case "TRANSIT_current" -> FOCUS_TRANSIT_CURRENT;
            case "TRANSIT_love" -> FOCUS_TRANSIT_LOVE;
            case "TRANSIT_career" -> FOCUS_TRANSIT_CAREER;
            case "TRANSIT_advice" -> FOCUS_TRANSIT_ADVICE;
            default -> "";
        };
    }
}

