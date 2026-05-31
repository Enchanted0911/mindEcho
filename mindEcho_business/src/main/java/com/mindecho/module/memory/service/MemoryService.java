package com.mindecho.module.memory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mindecho.module.memory.dto.MemoryDecisionResult;
import com.mindecho.module.memory.entity.Memory;
import com.mindecho.module.memory.mapper.MemoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 长期记忆服务（Memory Decision Engine + 智能存储路由）
 *
 * <h3>处理流水线</h3>
 * <pre>
 * Memory Decision Engine
 * │
 * ▼
 * 1. 记忆价值判断
 * 2. 重要性评分
 * 3. 记忆分类
 * 4. 存储路由决策
 * │
 * ┌──┼──┐
 * ▼  ▼  ▼
 * Memory表  memory_vector表  丢弃
 * </pre>
 *
 * <h3>存储路由规则</h3>
 * <ul>
 *   <li>importance >= 8 → 双写（Memory表 + memory_vector表）</li>
 *   <li>4~7 → 单写（FACT/PREFERENCE/GOAL/RELATIONSHIP/PROFILE → Memory表；CONVERSATION/EXPERIENCE/DISCUSSION → memory_vector表）</li>
 *   <li>&lt;= 3 → 丢弃</li>
 * </ul>
 *
 * <h3>记忆检索策略（重构后）</h3>
 * <ol>
 *   <li>Step1: 短期记忆（当前会话最近20轮，由 MessageChatMemoryAdvisor 处理）</li>
 *   <li>Step2: 从 Redis 全量加载结构化记忆（Memory表，按 userId 缓存）；Redis miss 时从 PgSQL 加载并回填缓存</li>
 *   <li>Step3: 向量 BM25 检索（memory_vector 表）+ 向量相似度检索（memory_vector 表），两路并行</li>
 *   <li>Step4: 合并去重 → BGE Rerank → Top5 注入 Prompt</li>
 * </ol>
 */
@Slf4j
@Service
public class MemoryService {

    private static final String SUMMARIZE_PROMPT = """
            你是一个专注于情绪健康的 AI 助手。请将以下记忆列表整合成一段简洁的记忆摘要（200字以内）。
            摘要应保留用户的关键状态、重要经历和个人特征，去掉重复内容。

            【待摘要记忆列表】
            %s

            请直接输出摘要内容，不要有任何前缀：
            """;

    private final MemoryMapper memoryMapper;
    private final ChatModel chatModel;
    private final MemoryVectorService memoryVectorService;
    private final MemoryDecisionEngine decisionEngine;
    private final MemoryCacheService memoryCacheService;

    public MemoryService(MemoryMapper memoryMapper,
                         @Qualifier("openAiChatModel") ChatModel chatModel,
                         MemoryVectorService memoryVectorService,
                         MemoryDecisionEngine decisionEngine,
                         MemoryCacheService memoryCacheService) {
        this.memoryMapper = memoryMapper;
        this.chatModel = chatModel;
        this.memoryVectorService = memoryVectorService;
        this.decisionEngine = decisionEngine;
        this.memoryCacheService = memoryCacheService;
    }

    // ─── 异步写入入口 ─────────────────────────────────────────────────────────

    /**
     * 异步提取并保存记忆（每条用户消息发送后调用）
     *
     * <p>经过三阶段决策引擎处理：
     * <ol>
     *   <li>价值判断：过滤简单问候/感谢等无价值消息</li>
     *   <li>重要性评分：0~10，低于4直接丢弃</li>
     *   <li>分类路由：根据类型决定写 Memory 表 / memory_vector 表 / 双写</li>
     * </ol>
     *
     * @param userId      用户 ID
     * @param userMessage 用户消息
     * @param aiResponse  AI 回复（暂时不存储，后续可扩展）
     */
    @Async
    public void extractAndSaveMemoryAsync(UUID userId, String userMessage, String aiResponse) {
        try {
            MemoryDecisionResult decision = decisionEngine.decide(userMessage);

            if (!decision.isNeedMemory()) {
                log.debug("MemoryService: discard message for userId={}, reason={}",
                        userId, decision.getDiscardReason());
                return;
            }

            log.debug("MemoryService: saving memory for userId={}, type={}, importance={}, writeMemory={}, writeVector={}",
                    userId, decision.getMemoryType(), decision.getImportanceScore(),
                    decision.isWriteToMemory(), decision.isWriteToVector());

            routeAndSave(userId, decision);

        } catch (Exception e) {
            log.error("MemoryService: async memory processing failed: userId={}", userId, e);
        }
    }

    // ─── 存储路由 ─────────────────────────────────────────────────────────────

    /**
     * 根据决策结果路由存储
     */
    private void routeAndSave(UUID userId, MemoryDecisionResult decision) {
        Memory savedMemory = null;

        // 写入 Memory 表（结构化长期记忆）
        if (decision.isWriteToMemory()) {
            savedMemory = saveToMemoryTable(userId, decision);
        }

        // 写入 memory_vector 表
        if (decision.isWriteToVector()) {
            String vectorId;
            String memoryId;

            if (savedMemory != null) {
                // 双写模式：使用 Memory 表的 ID 作为向量 ID（便于关联）
                vectorId = savedMemory.getId().toString();
                memoryId = savedMemory.getId().toString();
            } else {
                // 纯向量模式：生成新 UUID
                vectorId = UUID.randomUUID().toString();
                memoryId = vectorId;
            }

            memoryVectorService.upsertMemoryVector(
                    vectorId, memoryId,
                    userId.toString(),
                    decision.getMemoryType(),
                    decision.getContent(),
                    decision.getImportanceScore()
            );
        }
    }

    /**
     * 写入 Memory 表（支持 upsert 和 insert）
     */
    private Memory saveToMemoryTable(UUID userId, MemoryDecisionResult decision) {
        Memory saved;
        if (decision.isUpsert() && decision.getUpsertKey() != null && !decision.getUpsertKey().isBlank()) {
            saved = upsertByKey(userId, decision.getUpsertKey(), decision);
        } else {
            saved = insertMemory(userId, decision.getMemoryType(), decision.getContent(), decision.getImportanceScore());
        }
        // 写入 DB 后使 Redis 缓存失效，等下次请求重新加载
        memoryCacheService.evict(userId);
        return saved;
    }

    /**
     * 按唯一 Key 更新记忆（同一 key 只保留最新值，适用于 FACT/PROFILE 类型）
     */
    private Memory upsertByKey(UUID userId, String key, MemoryDecisionResult decision) {
        try {
            // 查找同类型同 key 前缀的旧记录（content 格式：key: value）
            List<Memory> existing = memoryMapper.selectList(
                    new LambdaQueryWrapper<Memory>()
                            .eq(Memory::getUserId, userId)
                            .eq(Memory::getMemoryType, decision.getMemoryType())
                            .eq(Memory::getDeleted, 0)
                            .likeRight(Memory::getContent, key + ":")
            );

            // 逻辑删除旧记录
            existing.forEach(m -> {
                m.setDeleted(1);
                memoryMapper.updateById(m);
                // 同步删除向量（如果存在）
                memoryVectorService.deleteByVectorId(m.getId().toString());
            });

            Memory newMemory = insertMemory(userId, decision.getMemoryType(),
                    key + ": " + decision.getContent(), decision.getImportanceScore());
            log.debug("MemoryService: upserted key={} for userId={}", key, userId);
            return newMemory;
        } catch (Exception e) {
            log.warn("MemoryService: upsert failed for userId={}, key={}: {}", userId, key, e.getMessage());
            return insertMemory(userId, decision.getMemoryType(), decision.getContent(), decision.getImportanceScore());
        }
    }

    // ─── 记忆加载（Redis 优先，供 LongTermMemoryAdvisor 调用） ──────────────

    /**
     * 加载用户全量结构化记忆（Redis 优先，miss 时从 PgSQL 加载并回填缓存）
     *
     * <p>返回所有非逻辑删除的 FACT/PREFERENCE/GOAL/RELATIONSHIP/PROFILE/SUMMARY 类型记忆，
     * 按 importanceScore 降序排列。
     *
     * @param userId 用户 ID
     * @return 全量结构化记忆列表
     */
    public List<Memory> loadAllStructuredMemories(UUID userId) {
        // 1. 先读 Redis
        List<Memory> cached = memoryCacheService.getFromCache(userId);
        if (cached != null) {
            return cached;
        }

        // 2. Redis miss → 从 PgSQL 加载全量
        List<Memory> memories = memoryMapper.selectList(
                new LambdaQueryWrapper<Memory>()
                        .eq(Memory::getUserId, userId)
                        .eq(Memory::getDeleted, 0)
                        .in(Memory::getMemoryType, List.of(
                                MemoryDecisionEngine.TYPE_FACT,
                                MemoryDecisionEngine.TYPE_PREFERENCE,
                                MemoryDecisionEngine.TYPE_GOAL,
                                MemoryDecisionEngine.TYPE_RELATIONSHIP,
                                MemoryDecisionEngine.TYPE_PROFILE,
                                MemoryDecisionEngine.TYPE_SUMMARY
                        ))
                        .orderByDesc(Memory::getImportanceScore)
        );

        // 3. 回填 Redis 缓存
        memoryCacheService.putToCache(userId, memories);
        log.debug("MemoryService: loaded {} structured memories from PgSQL for userId={}", memories.size(), userId);
        return memories;
    }

    /**
     * 批量更新 Memory 表中的召回次数和最近召回时间（召回后调用）
     *
     * @param memoryIds 被召回的记忆 ID 列表
     */
    @Transactional
    public void batchUpdateRecall(List<UUID> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) return;
        try {
            // 构造 PostgreSQL array 字符串
            String idsStr = memoryIds.stream()
                    .map(UUID::toString)
                    .collect(Collectors.joining(","));
            // 用 {uuid1,uuid2,...} 格式传入
            String pgArray = "{" + idsStr + "}";
            int updated = memoryMapper.batchUpdateRecall(pgArray);
            log.debug("MemoryService: updated recall for {} memory records", updated);
        } catch (Exception e) {
            log.warn("MemoryService: batchUpdateRecall failed: {}", e.getMessage());
        }
    }

    /**
     * 降级召回：按重要性排序（兼容旧接口，内部转为全量加载后截取）
     */
    public List<Memory> recallByImportance(UUID userId, int limit) {
        List<Memory> all = loadAllStructuredMemories(userId);
        return all.stream().limit(limit).toList();
    }

    // ─── 兼容旧接口（供其他模块调用）────────────────────────────────────────

    /**
     * 直接保存记忆（跳过决策引擎，用于系统内部写入如摘要压缩结果）
     * 写入后使 Redis 缓存失效。
     */
    public Memory saveMemory(UUID userId, String type, String content, int importanceScore) {
        List<Memory> existing = memoryMapper.selectList(
                new LambdaQueryWrapper<Memory>()
                        .eq(Memory::getUserId, userId)
                        .eq(Memory::getMemoryType, type)
                        .eq(Memory::getDeleted, 0)
                        .orderByDesc(Memory::getImportanceScore)
        );
        Memory result = existing.stream()
                .filter(m -> m.getContent().equals(content))
                .findFirst()
                .orElseGet(() -> insertAndVectorize(userId, type, content, importanceScore));
        // 使缓存失效
        memoryCacheService.evict(userId);
        return result;
    }

    public void deleteMemory(Memory memory) {
        memory.setDeleted(1);
        memoryMapper.updateById(memory);
        memoryVectorService.deleteByVectorId(memory.getId().toString());
        // 删除后使缓存失效
        memoryCacheService.evict(memory.getUserId());
    }

    /**
     * 获取用户所有结构化记忆（Redis 优先）
     */
    public List<Memory> getStructuredMemories(UUID userId) {
        return loadAllStructuredMemories(userId);
    }

    /**
     * 兼容旧接口：getProfileMemories
     */
    public List<Memory> getProfileMemories(UUID userId) {
        return getStructuredMemories(userId);
    }

    /**
     * 获取最新摘要记忆（降级时使用，从全量记忆中筛选）
     */
    public List<Memory> getSummaryMemories(UUID userId) {
        List<Memory> all = loadAllStructuredMemories(userId);
        return all.stream()
                .filter(m -> MemoryDecisionEngine.TYPE_SUMMARY.equals(m.getMemoryType()))
                .sorted((a, b) -> {
                    if (a.getCreatedTime() == null) return 1;
                    if (b.getCreatedTime() == null) return -1;
                    return b.getCreatedTime().compareTo(a.getCreatedTime());
                })
                .limit(3)
                .toList();
    }

    /**
     * 兼容旧接口：recallRelevantMemoryContents（供外部调用）
     * 改为从全量结构化记忆中返回 topK 条内容
     */
    public List<String> recallRelevantMemoryContents(UUID userId, String query) {
        return loadAllStructuredMemories(userId).stream()
                .limit(8)
                .map(Memory::getContent)
                .toList();
    }

    // ─── 摘要生成（供 MemoryForgetService 调用）─────────────────────────────

    /**
     * 调用 LLM 生成记忆摘要
     *
     * @param memoryContents 待摘要的记忆内容列表
     * @return 生成的摘要文本（失败时返回 null）
     */
    public String generateSummary(List<String> memoryContents) {
        if (memoryContents == null || memoryContents.isEmpty()) return null;
        try {
            String memoryText = memoryContents.stream()
                    .map(c -> "- " + c)
                    .collect(Collectors.joining("\n"));

            String result = chatModel.call(
                    new Prompt(new UserMessage(String.format(SUMMARIZE_PROMPT, memoryText))))
                    .getResult().getOutput().getText();

            if (result == null || result.isBlank()) {
                log.warn("MemoryService: LLM returned empty summary");
                return null;
            }
            return result.trim();
        } catch (Exception e) {
            log.error("MemoryService: summary generation failed: {}", e.getMessage());
            return null;
        }
    }

    // ─── 内部方法 ─────────────────────────────────────────────────────────────

    /**
     * 插入记忆到 Memory 表（不向量化，不清除缓存，由上层方法统一处理缓存失效）
     */
    Memory insertMemory(UUID userId, String type, String content, int importanceScore) {
        Memory memory = new Memory();
        memory.setUserId(userId);
        memory.setMemoryType(type);
        memory.setContent(content);
        memory.setImportanceScore(importanceScore);
        memory.setRecallCount(0L);
        memoryMapper.insert(memory);
        log.debug("MemoryService: inserted memory userId={}, type={}", userId, type);
        return memory;
    }

    /**
     * 插入记忆到 Memory 表并向量化（用于 SUMMARY 类型等）
     */
    private Memory insertAndVectorize(UUID userId, String type, String content, int importanceScore) {
        Memory memory = insertMemory(userId, type, content, importanceScore);
        // SUMMARY 类型也做向量化
        memoryVectorService.upsertMemoryVector(
                memory.getId().toString(), userId.toString(), type, content, importanceScore);
        return memory;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "…" : text;
    }
}

