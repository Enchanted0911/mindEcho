package com.mindecho.module.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * BGE Reranker 服务 — 调用 Ollama 部署的 dengcao/bge-reranker-v2-m3 模型
 *
 * <p>接口：POST /api/rerank（Ollama 标准 Rerank API）
 *
 * <p>输入：query + 候选文档列表
 * 输出：按 relevance_score 降序排列的 {@link RankedDocument} 列表
 *
 * <h3>用法示例</h3>
 * <pre>
 * List&lt;String&gt; docs = List.of("doc1 content", "doc2 content", ...);
 * List&lt;RerankService.RankedDocument&gt; ranked = rerankService.rerank(query, docs, 5, 0.3);
 * </pre>
 */
@Slf4j
@Service
public class RerankService {

    /** Ollama Rerank API 路径 */
    private static final String RERANK_PATH = "/api/rerank";

    /** HTTP 请求超时（秒） */
    private static final int HTTP_TIMEOUT_SECONDS = 15;

    @Value("${mindecho.reranker.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${mindecho.reranker.model:dengcao/bge-reranker-v2-m3}")
    private String rerankModel;

    /** 默认最终保留条数 */
    @Value("${mindecho.reranker.top-k:5}")
    private int defaultTopK;

    /** 默认 relevance_score 截断阈值（低于此值丢弃） */
    @Value("${mindecho.reranker.score-cutoff:0.05}")
    private double defaultScoreCutoff;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ─── 公共 API ─────────────────────────────────────────────────────────────

    /**
     * 对候选文档列表执行 BGE Rerank，返回按相关度降序且经过 topK + cutoff 过滤的结果。
     *
     * @param query     检索查询文本
     * @param documents 候选文档内容列表（与调用方持有的原始列表顺序一致，通过 index 映射）
     * @param topK      最终保留条数（-1 表示使用配置默认值）
     * @param cutoff    相关度截断阈值（-1 表示使用配置默认值）
     * @return 按 relevance_score 降序排列的结果列表；若 Rerank 调用失败，返回空列表（由调用方降级处理）
     */
    public List<RankedDocument> rerank(String query, List<String> documents,
                                       int topK, double cutoff) {
        if (query == null || query.isBlank() || documents == null || documents.isEmpty()) {
            return List.of();
        }

        int effectiveTopK = topK <= 0 ? defaultTopK : topK;
        double effectiveCutoff = cutoff < 0 ? defaultScoreCutoff : cutoff;

        try {
            String requestBody = buildRequestBody(query, documents);
            String responseBody = callOllamaRerank(requestBody);
            return parseResponse(responseBody, documents, effectiveTopK, effectiveCutoff);
        } catch (Exception e) {
            log.warn("RerankService: rerank failed, query='{}', docs={}, error={}",
                    truncate(query, 40), documents.size(), e.getMessage());
            return List.of();
        }
    }

    /**
     * 使用默认 topK 和 cutoff 进行 Rerank
     */
    public List<RankedDocument> rerank(String query, List<String> documents) {
        return rerank(query, documents, -1, -1);
    }

    // ─── 内部实现 ──────────────────────────────────────────────────────────────

    private String buildRequestBody(String query, List<String> documents) throws Exception {
        var bodyMap = new java.util.LinkedHashMap<String, Object>();
        bodyMap.put("model", rerankModel);
        bodyMap.put("query", query);
        bodyMap.put("documents", documents);
        return objectMapper.writeValueAsString(bodyMap);
    }

    private String callOllamaRerank(String requestBody) throws Exception {
        String url = ollamaBaseUrl.stripTrailing() + RERANK_PATH;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama rerank HTTP " + response.statusCode()
                    + ": " + truncate(response.body(), 200));
        }
        return response.body();
    }

    /**
     * 解析 Ollama /api/rerank 响应。
     *
     * <p>Ollama rerank 响应格式：
     * <pre>
     * {
     *   "model": "...",
     *   "results": [
     *     { "index": 0, "relevance_score": 0.89 },
     *     { "index": 2, "relevance_score": 0.73 },
     *     ...
     *   ]
     * }
     * </pre>
     */
    private List<RankedDocument> parseResponse(String responseBody, List<String> documents,
                                                int topK, double cutoff) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            log.warn("RerankService: empty results from ollama rerank");
            return List.of();
        }

        List<RankedDocument> ranked = new ArrayList<>();
        for (JsonNode item : results) {
            int index = item.path("index").asInt(-1);
            double score = item.path("relevance_score").asDouble(0.0);
            if (index < 0 || index >= documents.size()) continue;
            if (score < cutoff) continue;
            ranked.add(new RankedDocument(index, score, documents.get(index)));
        }

        // 按相关度降序（Ollama 通常已排序，但显式排序更安全）
        ranked.sort(Comparator.comparingDouble(RankedDocument::score).reversed());

        if (ranked.size() > topK) {
            ranked = ranked.subList(0, topK);
        }

        log.debug("RerankService: input={}, after_cutoff={}, topK={}, final={}",
                documents.size(), results.size(), topK, ranked.size());
        return ranked;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ─── 数据类 ────────────────────────────────────────────────────────────────

    /**
     * Rerank 结果：原始索引 + 相关度评分 + 文档内容
     *
     * @param index   原始列表中的位置（可用于映射回调用方的对象列表）
     * @param score   BGE Reranker 相关度评分（越高越相关）
     * @param content 文档内容文本
     */
    public record RankedDocument(int index, double score, String content) {}
}

