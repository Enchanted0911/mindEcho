package com.mindecho.module.chat.advisor;

import com.mindecho.module.astrology.service.UserAstrologyContextBuilder;
import com.mindecho.module.memory.entity.Memory;
import com.mindecho.module.memory.service.MemoryService;
import com.mindecho.module.memory.service.MemoryVectorService;
import com.mindecho.module.memory.service.RerankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 长期记忆注入 Advisor（BEFORE_MODEL 阶段）
 *
 * <p><b>重构后编排流程（四路合并 + Rerank + 星盘背景注入）</b>：
 * <pre>
 * Step 1  结构化记忆加载    MemoryService.loadAllStructuredMemories()
 *                          — 从 Redis 全量加载（miss 时从 PgSQL 加载回填缓存）
 *    ↓
 * Step 2  向量 BM25 召回   MemoryVectorService.searchByBm25()
 *                          — memory_vector 表关键词检索，topK×2 条
 *    ↓
 * Step 3  向量相似度召回   MemoryVectorService.searchCandidates()
 *                          — memory_vector 表语义检索，topK×2 条
 *    ↓
 * Step 4  合并去重         Advisor 自身
 *                          — 结构化记忆 + BM25向量 + 语义向量三路合并，内容去重
 *    ↓
 * Step 5  BGE Rerank       RerankService.rerank()
 *                          — dengcao/bge-reranker-v2-m3
 *    ↓
 * Step 6  TopK+Cutoff      Advisor 自身
 *                          — 过滤低分，取最终 TopK
 *    ↓
 * Step 7  注入 Prompt      Advisor 自身
 *                          — 拼接为 System Message 注入 LLM（含星盘背景上下文）
 * </pre>
 *
 * <p>当 query 为空时，跳过 Step 2–5，直接用结构化记忆按重要度 TopK 降级注入。
 * 当 BGE Reranker 不可用时，跳过 Step 5，以向量相似度+重要度加权排序降级。
 *
 * <p>星盘背景注入：从 user_astrology 表读取用户出生信息、本命盘摘要和最近解读，
 * 构建独立的 System Message 在记忆上下文之后注入，使主聊天模型能感知用户星盘背景。
 */
@Slf4j
public class LongTermMemoryAdvisor implements BaseAdvisor {

    /** Advisor 排序：先于 MessageChatMemoryAdvisor（order=0）执行 */
    public static final int ORDER = -10;

    /** 向量召回各路的倍率（= finalTopK × RECALL_MULTIPLIER） */
    private static final int RECALL_MULTIPLIER = 2;

    /** 最终注入 Prompt 的记忆条数 */
    private static final int FINAL_TOP_K = 5;

    /** BGE Reranker score 截断阈值，低于此值丢弃（-1 = 使用 RerankService 默认值） */
    private static final double SCORE_CUTOFF = 0.05;

    /** AdvisedRequest 上下文中存储 userId 的 key（由 ChatService 传入） */
    public static final String USER_ID_KEY = "mindecho_userId";

    private final MemoryService memoryService;
    private final MemoryVectorService memoryVectorService;
    private final RerankService rerankService;
    private final UserAstrologyContextBuilder astrologyContextBuilder;

    public LongTermMemoryAdvisor(MemoryService memoryService,
                                  MemoryVectorService memoryVectorService,
                                  RerankService rerankService,
                                  UserAstrologyContextBuilder astrologyContextBuilder) {
        this.memoryService = memoryService;
        this.memoryVectorService = memoryVectorService;
        this.rerankService = rerankService;
        this.astrologyContextBuilder = astrologyContextBuilder;
    }

    @Override
    public int getOrder() { return ORDER; }

    @Override
    public String getName() { return "LongTermMemoryAdvisor"; }

    // ─── BaseAdvisor ──────────────────────────────────────────────────────────

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        Object userIdObj = request.context().get(USER_ID_KEY);
        if (userIdObj == null) {
            log.debug("LongTermMemoryAdvisor: no userId in context, skip");
            return request;
        }
        UUID userId = parseUserId(userIdObj);
        if (userId == null) {
            return request;
        }

        String query = extractUserQuery(request);

        // Step A：长期记忆召回与注入
        String memoryContext = recallAndBuildContext(userId, query);

        // Step B：用户星盘背景注入（从 user_astrology 表，独立于 memory 表）
        String astrologyContext = buildAstrologyContext(userId);

        if ((memoryContext == null || memoryContext.isBlank())
                && (astrologyContext == null || astrologyContext.isBlank())) {
            return request;
        }

        log.debug("LongTermMemoryAdvisor: injecting context for userId={}, hasMemory={}, hasAstrology={}",
                userId, memoryContext != null, astrologyContext != null);
        return injectSystemMessages(request, memoryContext, astrologyContext);
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    // ─── 星盘背景构建 ─────────────────────────────────────────────────────────

    /**
     * 从 user_astrology 表加载并构建用户星盘背景上下文
     */
    private String buildAstrologyContext(UUID userId) {
        try {
            return astrologyContextBuilder.buildAstrologyContext(userId);
        } catch (Exception e) {
            log.warn("LongTermMemoryAdvisor: buildAstrologyContext failed userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    // ─── 核心编排：Step 1-7 ───────────────────────────────────────────────────

    /**
     * 执行完整的记忆召回流程并返回可直接注入 Prompt 的上下文文本。
     */
    private String recallAndBuildContext(UUID userId, String query) {
        int vectorCandidateLimit = FINAL_TOP_K * RECALL_MULTIPLIER;

        // ── Step 1：从 Redis 全量加载结构化记忆（Memory 表）────────────────────
        List<Memory> structuredMemories = loadStructuredMemories(userId);
        List<String> structuredContents = structuredMemories.stream()
                .map(Memory::getContent)
                .filter(c -> c != null && !c.isBlank())
                .toList();
        log.debug("LongTermMemoryAdvisor: [Step1] structured memories={} userId={}", structuredContents.size(), userId);

        // query 为空时直接用结构化记忆降级（跳过 Step 2-5）
        if (query == null || query.isBlank()) {
            List<String> fallback = structuredContents.stream().limit(FINAL_TOP_K).toList();
            return buildMemoryContext(fallback);
        }

        // ── Step 2：向量 BM25 召回（memory_vector 表）──────────────────────────
        List<String> bm25VectorContents = recallVectorBm25(userId, query, vectorCandidateLimit);
        log.debug("LongTermMemoryAdvisor: [Step2] vector BM25 recall={} userId={}", bm25VectorContents.size(), userId);

        // ── Step 3：向量相似度召回（memory_vector 表）──────────────────────────
        List<Document> vectorDocs = recallVector(userId, query, vectorCandidateLimit);
        List<String> vectorContents = vectorDocs.stream()
                .map(Document::getText)
                .filter(t -> t != null && !t.isBlank())
                .toList();
        log.debug("LongTermMemoryAdvisor: [Step3] vector similarity recall={} userId={}", vectorContents.size(), userId);

        // ── Step 4：三路合并去重 ─────────────────────────────────────────────
        // 优先级：向量语义 > 向量BM25 > 结构化记忆（结构化记忆通常为通用背景知识）
        List<String> mergedTexts = mergeAll(vectorContents, bm25VectorContents, structuredContents);
        log.debug("LongTermMemoryAdvisor: [Step4] merged candidates={} userId={}", mergedTexts.size(), userId);

        if (mergedTexts.isEmpty()) {
            return null;
        }

        // ── Step 5：BGE Rerank ────────────────────────────────────────────────
        List<RerankService.RankedDocument> ranked = rerankService.rerank(
                query, mergedTexts, FINAL_TOP_K, SCORE_CUTOFF);
        log.debug("LongTermMemoryAdvisor: [Step5] after BGE rerank={} userId={}", ranked.size(), userId);

        // ── Step 6：BGE 失败降级 ──────────────────────────────────────────────
        if (ranked.isEmpty()) {
            log.warn("LongTermMemoryAdvisor: BGE rerank unavailable, fallback to score-based ranking");
            List<String> fallback = fallbackSelect(vectorDocs, bm25VectorContents, structuredContents, FINAL_TOP_K);
            return buildMemoryContext(fallback);
        }

        // ── Step 7：提取最终内容，构建 Prompt 上下文 ─────────────────────────
        List<String> finalContents = ranked.stream()
                .map(RerankService.RankedDocument::content)
                .filter(t -> t != null && !t.isBlank())
                .toList();
        return buildMemoryContext(finalContents);
    }

    // ─── Step 1：加载结构化记忆 ──────────────────────────────────────────────

    private List<Memory> loadStructuredMemories(UUID userId) {
        try {
            return memoryService.loadAllStructuredMemories(userId);
        } catch (Exception e) {
            log.warn("LongTermMemoryAdvisor: loadStructuredMemories failed userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    // ─── Step 2：向量 BM25 召回 ──────────────────────────────────────────────

    private List<String> recallVectorBm25(UUID userId, String query, int limit) {
        try {
            return memoryVectorService.searchByBm25(userId.toString(), query, limit);
        } catch (Exception e) {
            log.warn("LongTermMemoryAdvisor: vector BM25 recall failed userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    // ─── Step 3：向量相似度召回 ───────────────────────────────────────────────

    private List<Document> recallVector(UUID userId, String query, int topK) {
        try {
            return memoryVectorService.searchCandidates(userId.toString(), query, topK);
        } catch (Exception e) {
            log.warn("LongTermMemoryAdvisor: vector recall failed userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    // ─── Step 4：三路合并去重 ─────────────────────────────────────────────────

    /**
     * 三路内容合并去重。
     * 优先级：向量语义结果 > 向量 BM25 结果 > 结构化记忆（提供用户背景上下文）
     *
     * @param vectorContents    向量相似度召回内容
     * @param bm25Contents      向量 BM25 召回内容
     * @param structuredContents 结构化记忆内容（全量，较多）
     * @return 去重后的合并内容列表
     */
    private List<String> mergeAll(List<String> vectorContents,
                                   List<String> bm25Contents,
                                   List<String> structuredContents) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        // 向量语义优先
        for (String text : vectorContents) {
            if (text != null && !text.isBlank()) seen.add(text);
        }
        // BM25 向量补充
        for (String text : bm25Contents) {
            if (text != null && !text.isBlank()) seen.add(text);
        }
        // 结构化记忆补充（兜底背景）
        for (String text : structuredContents) {
            if (text != null && !text.isBlank()) seen.add(text);
        }
        return new ArrayList<>(seen);
    }

    // ─── Step 6：降级排序（BGE 不可用时） ────────────────────────────────────

    /**
     * BGE Rerank 不可用时的降级策略：
     * 向量结果按相似度+重要度加权排序取 topK，不足时用 BM25 和结构化记忆补充。
     */
    private List<String> fallbackSelect(List<Document> vectorDocs,
                                         List<String> bm25Contents,
                                         List<String> structuredContents,
                                         int topK) {
        // 向量结果按加权分降序
        List<String> ranked = vectorDocs.stream()
                .sorted(Comparator.comparingDouble(this::vectorFallbackScore).reversed())
                .map(Document::getText)
                .filter(t -> t != null && !t.isBlank())
                .limit(topK)
                .collect(Collectors.toCollection(ArrayList::new));

        // 不足时用 BM25 向量结果补充
        Set<String> added = new HashSet<>(ranked);
        for (String text : bm25Contents) {
            if (ranked.size() >= topK) break;
            if (added.add(text)) ranked.add(text);
        }

        // 仍不足时用结构化记忆补充
        for (String text : structuredContents) {
            if (ranked.size() >= topK) break;
            if (added.add(text)) ranked.add(text);
        }
        return ranked;
    }

    /**
     * 向量结果降级评分：向量相似度（0.7）+ 重要度归一化（0.3）
     */
    private double vectorFallbackScore(Document doc) {
        double vectorScore = 0.5;
        Object scoreObj = doc.getMetadata().get("distance");
        if (scoreObj == null) {
            scoreObj = doc.getMetadata().get("score");
        }
        if (scoreObj != null) {
            try {
                vectorScore = 1.0 - (Double.parseDouble(scoreObj.toString()) / 2.0);
            } catch (NumberFormatException ignored) {}
        }

        double importanceNorm = 0.5;
        Object importObj = doc.getMetadata().get("importance_score");
        if (importObj != null) {
            try {
                importanceNorm = Math.min(Double.parseDouble(importObj.toString()) / 10.0, 1.0);
            } catch (NumberFormatException ignored) {}
        }
        return 0.7 * vectorScore + 0.3 * importanceNorm;
    }

    // ─── 上下文文本构建 ───────────────────────────────────────────────────────

    private String buildMemoryContext(List<String> contents) {
        if (contents == null || contents.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("【关于这位用户，你了解的信息】\n");
        contents.forEach(c -> sb.append("  - ").append(c).append("\n"));
        return sb.toString();
    }

    // ─── System Message 注入 ──────────────────────────────────────────────────

    /**
     * 将记忆上下文和星盘背景上下文注入到 Prompt System Messages 中。
     *
     * <p>注入策略：
     * <ul>
     *   <li>记忆上下文（存在时）：作为第一条额外 System Message 注入</li>
     *   <li>星盘背景上下文（存在时）：作为第二条额外 System Message 紧随其后注入</li>
     * </ul>
     */
    private ChatClientRequest injectSystemMessages(ChatClientRequest request,
                                                    String memoryContext,
                                                    String astrologyContext) {
        Prompt original = request.prompt();
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>(original.getInstructions());
        int insertIndex = findLastSystemMessageIndex(messages);

        // 按顺序插入（星盘背景先插入，记忆上下文在其前面，最终顺序：记忆 → 星盘背景）
        // 注意：每次在 insertIndex+1 位置插入，所以后插入的在前面
        if (astrologyContext != null && !astrologyContext.isBlank()) {
            messages.add(insertIndex + 1, new SystemMessage(astrologyContext));
        }
        if (memoryContext != null && !memoryContext.isBlank()) {
            messages.add(insertIndex + 1, new SystemMessage(memoryContext));
        }

        return request.mutate().prompt(new Prompt(messages, original.getOptions())).build();
    }

    private int findLastSystemMessageIndex(List<org.springframework.ai.chat.messages.Message> messages) {
        int last = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof SystemMessage) {
                last = i;
            }
        }
        return last;
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────────

    private UUID parseUserId(Object obj) {
        try {
            return obj instanceof UUID u ? u : UUID.fromString(obj.toString());
        } catch (Exception e) {
            log.warn("LongTermMemoryAdvisor: invalid userId={}", obj);
            return null;
        }
    }

    private String extractUserQuery(ChatClientRequest request) {
        try {
            var messages = request.prompt().getInstructions();
            for (int i = messages.size() - 1; i >= 0; i--) {
                var msg = messages.get(i);
                if (msg instanceof org.springframework.ai.chat.messages.UserMessage) {
                    return msg.getText();
                }
            }
        } catch (Exception e) {
            log.debug("LongTermMemoryAdvisor: failed to extract user query: {}", e.getMessage());
        }
        return null;
    }
}

