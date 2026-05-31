package com.mindecho.module.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindecho.module.memory.dto.MemoryDecisionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 记忆决策引擎（Memory Decision Engine）
 *
 * <p>三阶段处理流水线：
 * <ol>
 *   <li>记忆价值判断 — 判断消息是否值得进入长期记忆体系</li>
 *   <li>重要性评分  — 评估记忆价值（0~10）</li>
 *   <li>记忆分类   — 分类为具体记忆类型，决定存储路由</li>
 * </ol>
 *
 * <p>存储路由规则：
 * <ul>
 *   <li>importance &gt;= 8 → 双写（Memory表 + memory_vector表）</li>
 *   <li>4~7            → 单写（根据 memory_type 决定写哪张表）</li>
 *   <li>&lt;= 3        → 丢弃</li>
 * </ul>
 *
 * <p>类型路由：
 * <ul>
 *   <li>FACT / PREFERENCE / GOAL / RELATIONSHIP / PROFILE → Memory 表</li>
 *   <li>CONVERSATION / EXPERIENCE / DISCUSSION           → memory_vector 表</li>
 * </ul>
 */
@Slf4j
@Service
public class MemoryDecisionEngine {

    // ─── 记忆类型常量 ─────────────────────────────────────────────────────────

    /** 事实记忆 → 长期记忆表 */
    public static final String TYPE_FACT         = "FACT";
    /** 偏好记忆 → 长期记忆表 */
    public static final String TYPE_PREFERENCE   = "PREFERENCE";
    /** 目标记忆 → 长期记忆表 */
    public static final String TYPE_GOAL         = "GOAL";
    /** 关系记忆 → 长期记忆表 */
    public static final String TYPE_RELATIONSHIP = "RELATIONSHIP";
    /** 综合画像（兼容旧数据）→ 长期记忆表 */
    public static final String TYPE_PROFILE      = "PROFILE";
    /** 记忆摘要 → 长期记忆表 */
    public static final String TYPE_SUMMARY      = "SUMMARY";
    /** 普通对话 → 向量记忆表 */
    public static final String TYPE_CONVERSATION = "CONVERSATION";
    /** 经历记忆 → 向量记忆表 */
    public static final String TYPE_EXPERIENCE   = "EXPERIENCE";
    /** 讨论记忆 → 向量记忆表 */
    public static final String TYPE_DISCUSSION   = "DISCUSSION";

    // ─── 存储路由阈值 ─────────────────────────────────────────────────────────

    /** 双写阈值：importance >= 8 时同时写 Memory 表和向量表 */
    private static final int DUAL_WRITE_THRESHOLD = 8;
    /** 单写阈值：importance >= 4 且 < 8 时单写 */
    private static final int SINGLE_WRITE_THRESHOLD = 4;

    // ─── LLM Prompt 模板 ─────────────────────────────────────────────────────

    /**
     * 三阶段统一决策 Prompt（一次 LLM 调用完成全部判断，减少 API 消耗）
     *
     * <p>输出格式：
     * <pre>
     * {
     *   "need_memory": true,
     *   "discard_reason": "",
     *   "importance_score": 8,
     *   "memory_type": "FACT",
     *   "content": "用户名叫Johnson",
     *   "upsert": true,
     *   "upsert_key": "name"
     * }
     * </pre>
     */
    private static final String DECISION_PROMPT = """
            你是一个记忆分析引擎，负责判断用户发言是否值得被长期记忆，并完成分类和提炼。

            【第一阶段：记忆价值判断】
            不存储：简单问候（你好/嗨/在吗）、感谢（谢谢/好的/收到）、表情符号、无实质内容的闲聊
            存储：身份信息、偏好、长期目标、关系、重要事件、情绪模式、经历、想法讨论

            【第二阶段：重要性评分（0~10）】
            0~3  → 低价值（会丢弃）
            4~6  → 普通记忆
            7~8  → 重要记忆
            9~10 → 核心记忆（重大人生事件、重要身份信息）

            评分参考：
            - "喜欢奶茶" → 5
            - "我叫Johnson" → 9
            - "准备考雅思" → 8
            - "即将结婚" → 10
            - "今天天气不错" → 2

            【第三阶段：记忆分类】
            FACT        — 事实信息（我叫X / 我是程序员 / 我今年28岁）
            PREFERENCE  — 偏好（喜欢咖啡 / 不喜欢运动）
            GOAL        — 目标计划（要考雅思 / 打算换工作）
            RELATIONSHIP— 人际关系（我老婆叫Lisa / 我有个哥哥）
            PROFILE     — 综合画像（不确定具体类型时使用）
            CONVERSATION— 普通对话（昨天去旅行了）
            EXPERIENCE  — 重要经历（上周参加了...比赛）
            DISCUSSION  — 想法讨论（关于...我觉得...）

            【存储目标说明】
            - FACT/PREFERENCE/GOAL/RELATIONSHIP/PROFILE → 写入结构化记忆表
            - CONVERSATION/EXPERIENCE/DISCUSSION → 写入向量记忆表

            【upsert 说明】
            - 当 memory_type 为 FACT/PROFILE 且内容是可唯一标识的画像维度时，upsert=true
            - upsert_key 为画像维度名称（如 "name"、"occupation"、"age"）
            - 其他情况 upsert=false，upsert_key=""

            【输出要求】
            - 只返回 JSON，不要任何解释
            - content 字段为提炼后的简洁记忆文本（20字以内），不包含"用户"前缀
            - need_memory=false 时，importance_score=0，其他字段可为空

            用户发言："%s"

            输出 JSON：
            """;

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public MemoryDecisionEngine(@Qualifier("openAiChatModel") ChatModel chatModel,
                                ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    // ─── 核心入口 ─────────────────────────────────────────────────────────────

    /**
     * 执行三阶段记忆决策
     *
     * @param userMessage 用户消息内容
     * @return 决策结果（包含存储路由信息）
     */
    public MemoryDecisionResult decide(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return discardResult("消息为空");
        }

        try {
            String raw = chatModel.call(
                    new Prompt(new UserMessage(String.format(DECISION_PROMPT, userMessage))))
                    .getResult().getOutput().getText();

            if (raw == null || raw.isBlank()) {
                log.warn("MemoryDecisionEngine: LLM returned empty response for message='{}'",
                        truncate(userMessage, 50));
                return discardResult("LLM 返回空响应");
            }

            return parseAndRoute(raw, userMessage);

        } catch (Exception e) {
            log.error("MemoryDecisionEngine: decision failed for message='{}': {}",
                    truncate(userMessage, 50), e.getMessage());
            return discardResult("决策异常: " + e.getMessage());
        }
    }

    // ─── 解析与路由 ───────────────────────────────────────────────────────────

    private MemoryDecisionResult parseAndRoute(String raw, String userMessage) {
        try {
            // 清理 markdown 代码块包装
            String cleaned = raw.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```json", "").replaceAll("```", "").trim();
            }

            JsonNode root = objectMapper.readTree(cleaned);

            boolean needMemory = root.path("need_memory").asBoolean(false);
            if (!needMemory) {
                String reason = root.path("discard_reason").asText("低价值消息");
                log.debug("MemoryDecisionEngine: discard '{}', reason='{}'",
                        truncate(userMessage, 30), reason);
                return discardResult(reason);
            }

            int importanceScore = root.path("importance_score").asInt(0);
            String memoryType   = root.path("memory_type").asText("CONVERSATION").toUpperCase();
            String content      = root.path("content").asText("").trim();
            boolean upsert      = root.path("upsert").asBoolean(false);
            String upsertKey    = root.path("upsert_key").asText("").trim();

            // 内容为空时用原始消息的截断版
            if (content.isEmpty()) {
                content = truncate(userMessage, 100);
            }

            // 低价值直接丢弃
            if (importanceScore < SINGLE_WRITE_THRESHOLD) {
                log.debug("MemoryDecisionEngine: low importance ({}) discard '{}'",
                        importanceScore, truncate(userMessage, 30));
                return discardResult("重要性评分过低: " + importanceScore);
            }

            // 计算存储路由
            boolean isStructuredType = isStructuredType(memoryType);
            boolean isVectorType     = isVectorType(memoryType);

            boolean writeToMemory;
            boolean writeToVector;

            if (importanceScore >= DUAL_WRITE_THRESHOLD) {
                // 双写：importance >= 8
                writeToMemory = true;
                writeToVector = true;
            } else {
                // 单写：按类型路由
                writeToMemory = isStructuredType;
                writeToVector = isVectorType;
            }

            // 兜底：确保至少写一个
            if (!writeToMemory && !writeToVector) {
                writeToMemory = isStructuredType;
                writeToVector = !writeToMemory;
            }

            log.debug("MemoryDecisionEngine: decided type={}, importance={}, writeMemory={}, writeVector={}",
                    memoryType, importanceScore, writeToMemory, writeToVector);

            return MemoryDecisionResult.builder()
                    .needMemory(true)
                    .importanceScore(importanceScore)
                    .memoryType(memoryType)
                    .content(content)
                    .writeToMemory(writeToMemory)
                    .writeToVector(writeToVector)
                    .upsert(upsert)
                    .upsertKey(upsertKey)
                    .build();

        } catch (Exception e) {
            log.warn("MemoryDecisionEngine: parse failed: {}, raw='{}'",
                    e.getMessage(), truncate(raw, 100));
            return discardResult("解析失败: " + e.getMessage());
        }
    }

    // ─── 类型判断 ─────────────────────────────────────────────────────────────

    /**
     * 是否为结构化存储类型（写入 Memory 表）
     */
    public static boolean isStructuredType(String type) {
        return TYPE_FACT.equals(type)
                || TYPE_PREFERENCE.equals(type)
                || TYPE_GOAL.equals(type)
                || TYPE_RELATIONSHIP.equals(type)
                || TYPE_PROFILE.equals(type)
                || TYPE_SUMMARY.equals(type);
    }

    /**
     * 是否为向量存储类型（写入 memory_vector 表）
     */
    public static boolean isVectorType(String type) {
        return TYPE_CONVERSATION.equals(type)
                || TYPE_EXPERIENCE.equals(type)
                || TYPE_DISCUSSION.equals(type);
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────────

    private static MemoryDecisionResult discardResult(String reason) {
        return MemoryDecisionResult.builder()
                .needMemory(false)
                .discardReason(reason)
                .importanceScore(0)
                .writeToMemory(false)
                .writeToVector(false)
                .build();
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "…" : text;
    }
}

