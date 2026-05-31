package com.mindecho.module.memory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆向量存储服务（memory_vector 表 / pgvector）
 *
 * <p><b>职责边界</b>：仅负责 memory_vector 表的读写操作。
 * <ul>
 *   <li>写入：向量 upsert / delete</li>
 *   <li>读取：按 userId 做向量相似度检索（不做 Rerank，不合并 BM25）</li>
 *   <li>统计：召回次数更新、记忆分数查询</li>
 * </ul>
 *
 * <p><b>召回与 Rerank 编排</b>由 {@code LongTermMemoryAdvisor} 负责，
 * 调用顺序为：BM25 召回 → 向量召回 → 合并去重 → BGE Rerank → TopK+Cutoff 过滤。
 *
 * <p>元数据 key 约定：
 * <ul>
 *   <li>{@code memory_id}        — 对应 {@code memory.id}（UUID 字符串）</li>
 *   <li>{@code user_id}          — 用户 ID（UUID 字符串）</li>
 *   <li>{@code memory_type}      — 记忆类型</li>
 *   <li>{@code importance_score} — 重要度 0-10</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryVectorService {

    /** 相似度阈值（宽松，确保跨会话记忆能进入候选池） */
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.4;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    // ─── 向量写入 ──────────────────────────────────────────────────────────────

    /**
     * 将记忆内容向量化并写入 memory_vector。
     *
     * @param vectorId        向量记录的 ID（UUID 字符串）
     * @param memoryId        关联的 memory.id（UUID 字符串，纯向量记忆时可以相同）
     * @param userId          用户 ID（UUID 字符串）
     * @param memoryType      记忆类型
     * @param content         记忆文本内容
     * @param importanceScore 重要度 0–10
     */
    public void upsertMemoryVector(String vectorId, String memoryId, String userId,
                                    String memoryType, String content, int importanceScore) {
        try {
            deleteByVectorId(vectorId); // 先删除旧向量（幂等）

            Map<String, Object> metadata = new HashMap<>(4);
            metadata.put("memory_id", memoryId);
            metadata.put("user_id", userId);
            metadata.put("memory_type", memoryType);
            metadata.put("importance_score", String.valueOf(importanceScore));

            vectorStore.add(List.of(new Document(vectorId, content, metadata)));
            log.debug("MemoryVector upserted: vectorId={}, userId={}, type={}", vectorId, userId, memoryType);
        } catch (Exception e) {
            log.warn("Failed to upsert memory vector: vectorId={}, userId={}", vectorId, userId, e);
        }
    }

    /** 兼容旧接口：vectorId = memoryId */
    public void upsertMemoryVector(String memoryId, String userId, String memoryType,
                                    String content, int importanceScore) {
        upsertMemoryVector(memoryId, memoryId, userId, memoryType, content, importanceScore);
    }

    /** 按 vectorId 删除向量条目 */
    public void deleteByVectorId(String vectorId) {
        try {
            vectorStore.delete(List.of(vectorId));
        } catch (Exception e) {
            log.warn("Failed to delete memory vector: vectorId={}", vectorId, e);
        }
    }

    /** 兼容旧接口 */
    public void deleteByMemoryId(String memoryId) {
        deleteByVectorId(memoryId);
    }

    // ─── BM25 检索（memory_vector 表）────────────────────────────────────────

    /**
     * 对 memory_vector 表执行 BM25 全文检索（PostgreSQL ts_rank + to_tsvector）。
     *
     * <p>使用 {@code simple} 配置，适合短文本；对 content 列做分词索引匹配。
     * 返回按相关度降序的文本列表，供 {@code LongTermMemoryAdvisor} 与向量召回合并后 Rerank。
     *
     * @param userId 用户 ID（UUID 字符串）
     * @param query  检索文本
     * @param limit  返回条数上限
     * @return 按 BM25 相关度降序的内容文本列表（不含逻辑删除记录）
     */
    public List<String> searchByBm25(String userId, String query, int limit) {
        if (userId == null || query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        try {
            List<String> results = jdbcTemplate.queryForList(
                    """
                    SELECT content
                    FROM memory_vector
                    WHERE (metadata->>'user_id') = ?
                      AND to_tsvector('simple', content) @@ plainto_tsquery('simple', ?)
                    ORDER BY ts_rank(to_tsvector('simple', content), plainto_tsquery('simple', ?)) DESC
                    LIMIT ?
                    """,
                    String.class, userId, query, query, limit);
            log.debug("MemoryVectorService: BM25 recall userId={}, query='{}', results={}",
                    userId, truncate(query, 30), results.size());
            return results;
        } catch (Exception e) {
            log.warn("MemoryVectorService: BM25 search failed userId={}, error={}", userId, e.getMessage());
            return List.of();
        }
    }

    // ─── 向量检索（纯粹，不做 Rerank）────────────────────────────────────────

    /**
     * 按 userId 做向量相似度检索，返回 topK 条候选（不做任何 Rerank）。
     *
     * <p>调用方（{@code LongTermMemoryAdvisor}）负责将结果与 BM25 候选合并后交给
     * {@link RerankService} 统一 Rerank。
     *
     * @param userId 用户 ID（UUID 字符串）
     * @param query  检索文本
     * @param topK   候选数量上限
     * @return 按向量相似度降序的候选 Document 列表（携带完整 metadata）
     */
    public List<Document> searchCandidates(String userId, String query, int topK) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression(b.eq("user_id", userId).build())
                        .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD)
                        .build()
        );
    }

    /**
     * 按 userId + memoryType 做向量检索（不做 Rerank）
     */
    public List<Document> searchCandidatesByType(String userId, String memoryType,
                                                  String query, int topK) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression(
                                b.and(
                                        b.eq("user_id", userId),
                                        b.eq("memory_type", memoryType)
                                ).build()
                        )
                        .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD)
                        .build()
        );
    }

    /**
     * 按多个 memoryType 检索，合并结果（不做 Rerank）
     */
    public List<Document> searchCandidatesByTypes(String userId, List<String> memoryTypes,
                                                   String query, int topKPerType) {
        return memoryTypes.stream()
                .flatMap(type -> searchCandidatesByType(userId, type, query, topKPerType).stream())
                .toList();
    }

    // ─── 召回次数更新 ──────────────────────────────────────────────────────────

    /**
     * 批量更新 memory_vector 的召回次数和最近召回时间
     *
     * @param vectorIds 向量记录 ID 列表（UUID 字符串）
     */
    public void batchUpdateVectorRecall(List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        try {
            String idList = vectorIds.stream()
                    .map(id -> "'" + id + "'")
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            if (idList.isEmpty()) {
                return;
            }
            int updated = jdbcTemplate.update("""
                    UPDATE memory_vector
                    SET recall_count       = COALESCE(recall_count, 0) + 1,
                        last_recalled_time = NOW()
                    WHERE id IN (%s)
                    """.formatted(idList));
            log.debug("MemoryVectorService: updated recall for {} vector records", updated);
        } catch (Exception e) {
            log.warn("MemoryVectorService: batchUpdateVectorRecall failed: {}", e.getMessage());
        }
    }

    /**
     * 查询指定用户向量记忆中的最大召回次数（用于 frequency_norm 归一化）
     */
    public long getMaxVectorRecallCount(String userId) {
        try {
            Long max = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(recall_count), 0) FROM memory_vector WHERE (metadata->>'user_id') = ?",
                    Long.class, userId);
            return max != null ? max : 0L;
        } catch (Exception e) {
            log.warn("MemoryVectorService: getMaxVectorRecallCount failed: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 查询指定用户向量记忆总条数
     */
    public long countByUserId(String userId) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM memory_vector WHERE (metadata->>'user_id') = ?",
                    Long.class, userId);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("MemoryVectorService: countByUserId failed: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 查询 memory_score 最低的 N 条向量记忆 ID（用于遗忘压缩候选）
     */
    public List<String> findLowestScoreVectorIds(String userId, int limit) {
        try {
            return jdbcTemplate.queryForList(
                    """
                    SELECT id::text
                    FROM memory_vector
                    WHERE (metadata->>'user_id') = ?
                      AND memory_score IS NOT NULL
                    ORDER BY memory_score ASC
                    LIMIT ?
                    """,
                    String.class, userId, limit);
        } catch (Exception e) {
            log.warn("MemoryVectorService: findLowestScoreVectorIds failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 查询向量记忆详情（供摘要生成使用）
     */
    public List<Map<String, Object>> findVectorMemoryDetails(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        try {
            String idList = ids.stream()
                    .map(id -> "'" + id + "'")
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            return jdbcTemplate.queryForList(
                    "SELECT id::text, content FROM memory_vector WHERE id::text IN (" + idList + ")");
        } catch (Exception e) {
            log.warn("MemoryVectorService: findVectorMemoryDetails failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 物理删除向量记忆（遗忘压缩后删除低分记忆）
     */
    public void physicalDeleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            vectorStore.delete(ids);
        } catch (Exception e) {
            log.warn("MemoryVectorService: physicalDeleteByIds failed: {}", e.getMessage());
        }
    }

    // ─── 静态工具方法 ──────────────────────────────────────────────────────────

    /** 从 Document 元数据中提取 memory_id */
    public static String extractMemoryId(Document doc) {
        Object raw = doc.getMetadata().get("memory_id");
        return raw != null ? raw.toString() : null;
    }

    /** 从 Document 列表中提取文本内容 */
    public static List<String> extractContents(List<Document> docs) {
        return docs.stream().map(Document::getText).toList();
    }

    /** 提取 Document 的 ID 列表 */
    public static List<String> extractIds(List<Document> docs) {
        return docs.stream().map(Document::getId).toList();
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "…" : text;
    }
}

