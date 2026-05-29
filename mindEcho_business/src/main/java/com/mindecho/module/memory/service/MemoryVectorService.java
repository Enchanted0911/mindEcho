package com.mindecho.module.memory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆向量存储服务（pgvector / Spring AI）
 *
 * <p>元数据 key 约定：
 * <ul>
 *   <li>{@code memory_id}    — 对应 {@code memory.id}（UUID 字符串）</li>
 *   <li>{@code user_id}      — 用户 ID（UUID 字符串）</li>
 *   <li>{@code memory_type}  — 记忆类型</li>
 *   <li>{@code importance_score} — 重要度 1-10</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryVectorService {

    private final VectorStore vectorStore;

    // ─── 向量写入 ──────────────────────────────────────────────────────────

    /**
     * 将记忆内容向量化并写入 pgvector
     *
     * @param memoryId      关联的 memory.id（UUID 字符串）
     * @param userId        用户 ID（UUID 字符串）
     * @param memoryType    记忆类型
     * @param content       记忆文本内容
     * @param importanceScore 重要度 1–10
     */
    public void upsertMemoryVector(String memoryId, String userId, String memoryType,
                                   String content, int importanceScore) {
        try {
            deleteByMemoryId(memoryId);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("memory_id", memoryId);
            metadata.put("user_id", userId);
            metadata.put("memory_type", memoryType);
            metadata.put("importance_score", String.valueOf(importanceScore));

            Document doc = new Document("mem_" + memoryId, content, metadata);
            vectorStore.add(List.of(doc));
            log.debug("Memory vector upserted: memoryId={}, userId={}, type={}", memoryId, userId, memoryType);
        } catch (Exception e) {
            log.warn("Failed to upsert memory vector: memoryId={}, userId={}", memoryId, userId, e);
        }
    }

    /**
     * 按 memory_id 删除向量条目（软删除记忆时调用）
     */
    public void deleteByMemoryId(String memoryId) {
        try {
            vectorStore.delete(List.of("mem_" + memoryId));
        } catch (Exception e) {
            log.warn("Failed to delete memory vector: memoryId={}", memoryId, e);
        }
    }

    // ─── 语义检索 ──────────────────────────────────────────────────────────

    /**
     * 按语义 query 检索指定用户的相关记忆，不限类型
     *
     * @param userId 用户 ID（UUID 字符串）
     * @param query  查询文本
     * @param topK   返回最多 K 条
     */
    public List<Document> searchRelevantMemories(String userId, String query, int topK) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression(b.eq("user_id", userId).build())
                        .similarityThreshold(0.70)
                        .build()
        );
    }

    /**
     * 按语义 query 检索指定用户指定类型的记忆
     */
    public List<Document> searchMemoriesByType(String userId, String memoryType,
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
                        .similarityThreshold(0.70)
                        .build()
        );
    }

    /**
     * 按语义 query 检索指定用户、多个记忆类型的记忆，合并结果
     */
    public List<Document> searchMemoriesByTypes(String userId, List<String> memoryTypes,
                                                String query, int topKPerType) {
        return memoryTypes.stream()
                .flatMap(type -> searchMemoriesByType(userId, type, query, topKPerType).stream())
                .toList();
    }

    /**
     * 从 Document 元数据中提取 memory_id（UUID 字符串）
     */
    public static String extractMemoryId(Document doc) {
        Object raw = doc.getMetadata().get("memory_id");
        return raw != null ? raw.toString() : null;
    }

    /**
     * 从 Document 列表中提取记忆内容文本
     */
    public static List<String> extractContents(List<Document> docs) {
        return docs.stream()
                .map(Document::getText)
                .toList();
    }
}

