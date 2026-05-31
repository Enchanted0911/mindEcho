package com.mindecho.module.memory.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 记忆决策引擎输出结果
 *
 * <p>经过三阶段处理后的最终决策：
 * <ol>
 *   <li>记忆价值判断（need_memory）</li>
 *   <li>重要性评分（importance_score 0~10）</li>
 *   <li>记忆分类（memory_type）</li>
 * </ol>
 *
 * <p>存储路由规则：
 * <ul>
 *   <li>importance >= 8 → 双写（Memory表 + memory_vector表）</li>
 *   <li>4~7 → 单写（根据 memory_type 决定写哪张表）</li>
 *   <li>&lt;= 3 → 丢弃</li>
 * </ul>
 *
 * <p>类型路由规则：
 * <ul>
 *   <li>FACT / PREFERENCE / GOAL / RELATIONSHIP / PROFILE → Memory 表（长期记忆）</li>
 *   <li>CONVERSATION / EXPERIENCE / DISCUSSION → memory_vector 表（向量记忆）</li>
 * </ul>
 */
@Data
@Builder
public class MemoryDecisionResult {

    /**
     * 是否需要存储（第一阶段：价值判断）
     * false 时直接丢弃，不进入后续阶段
     */
    private boolean needMemory;

    /**
     * 丢弃原因（needMemory=false 时填充）
     */
    private String discardReason;

    /**
     * 重要性评分 0~10（第二阶段）
     */
    private int importanceScore;

    /**
     * 记忆类型（第三阶段）
     * FACT / PREFERENCE / GOAL / RELATIONSHIP / PROFILE /
     * CONVERSATION / EXPERIENCE / DISCUSSION / SUMMARY
     */
    private String memoryType;

    /**
     * 提取出的记忆内容（由 LLM 提炼，去除无关信息）
     */
    private String content;

    /**
     * 是否写入 Memory 表（结构化长期记忆）
     */
    private boolean writeToMemory;

    /**
     * 是否写入 memory_vector 表（向量记忆）
     */
    private boolean writeToVector;

    /**
     * 是否需要 upsert（按 key 更新而非新增，适用于 FACT/PROFILE 类型）
     */
    private boolean upsert;

    /**
     * upsert 时的匹配 key（如 "name" / "occupation" 等画像维度）
     */
    private String upsertKey;
}

