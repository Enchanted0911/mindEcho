package com.mindecho.module.astrology.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import com.mindecho.module.astrology.dto.BirthInfoDTO;
import com.mindecho.module.astrology.dto.NatalChartResponseDTO;
import com.mindecho.module.astrology.dto.SynastryResponseDTO;
import com.mindecho.module.astrology.dto.TransitResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Python 占星服务 HTTP 客户端
 *
 * <p>封装所有 Java → Python 的内部 HTTP 调用，包含：
 * <ul>
 *   <li>本命盘计算：POST /internal/natal</li>
 *   <li>和盘计算：POST /internal/synastry</li>
 *   <li>流运计算：POST /internal/transit</li>
 *   <li>RAG 检索：POST /internal/rag/query</li>
 * </ul>
 *
 * <p>超时策略（按 PRD 要求）：
 * <ul>
 *   <li>natal / transit：3s</li>
 *   <li>synastry / rag：5s</li>
 * </ul>
 */
@Slf4j
@Component
public class AstrologyPythonClient {

    private static final Duration TIMEOUT_NATAL = Duration.ofSeconds(3);
    private static final Duration TIMEOUT_SYNASTRY = Duration.ofSeconds(5);
    private static final Duration TIMEOUT_TRANSIT = Duration.ofSeconds(3);
    private static final Duration TIMEOUT_RAG = Duration.ofSeconds(5);

    private static final String PATH_NATAL = "/internal/natal";
    private static final String PATH_SYNASTRY = "/internal/synastry";
    private static final String PATH_TRANSIT = "/internal/transit";
    private static final String PATH_RAG = "/internal/rag/query";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${astrology.python-service.base-url:http://python-astrology-service:8000}")
    private String pythonBaseUrl;

    public AstrologyPythonClient(WebClient.Builder webClientBuilder,
                                  @Qualifier("webObjectMapper") ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    // ─────────────────────── 本命盘 ───────────────────────

    /**
     * 调用 Python 服务计算本命盘
     *
     * @param birthInfo 出生信息
     * @return 本命盘计算结果（chart + summary）
     */
    public NatalChartResponseDTO calculateNatal(BirthInfoDTO birthInfo) {
        log.info("Calling Python natal: city={}, date={}-{}-{}",
                birthInfo.getCity(), birthInfo.getYear(), birthInfo.getMonth(), birthInfo.getDay());
        try {
            String requestBody = objectMapper.writeValueAsString(birthInfo);
            String responseBody = post(PATH_NATAL, requestBody, TIMEOUT_NATAL);

            JsonNode root = objectMapper.readTree(responseBody);
            return NatalChartResponseDTO.builder()
                    .chart(root.get("chart"))
                    .summary(root.get("summary"))
                    .savedToProfile(false)
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Python natal calculation failed", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }

    // ─────────────────────── 和盘 ───────────────────────

    /**
     * 调用 Python 服务计算和盘
     *
     * @param selfBirth    本人出生信息
     * @param partnerBirth 对方出生信息
     * @return 和盘计算结果
     */
    public SynastryResponseDTO calculateSynastry(BirthInfoDTO selfBirth, BirthInfoDTO partnerBirth) {
        log.info("Calling Python synastry");
        try {
            com.fasterxml.jackson.databind.node.ObjectNode reqNode = objectMapper.createObjectNode();
            reqNode.set("self", objectMapper.valueToTree(selfBirth));
            reqNode.set("partner", objectMapper.valueToTree(partnerBirth));

            String requestBody = objectMapper.writeValueAsString(reqNode);
            String responseBody = post(PATH_SYNASTRY, requestBody, TIMEOUT_SYNASTRY);

            JsonNode root = objectMapper.readTree(responseBody);

            List<JsonNode> aspects = new ArrayList<>();
            if (root.has("aspects") && root.get("aspects").isArray()) {
                root.get("aspects").forEach(aspects::add);
            }

            List<String> themes = new ArrayList<>();
            if (root.has("themes") && root.get("themes").isArray()) {
                root.get("themes").forEach(n -> themes.add(n.asText()));
            }

            return SynastryResponseDTO.builder()
                    .relationshipModel(root.get("relationship_model"))
                    .aspects(aspects)
                    .themes(themes)
                    .chart(root)
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Python synastry calculation failed", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }

    // ─────────────────────── 流运 ───────────────────────

    /**
     * 调用 Python 服务计算流运
     *
     * @param birthInfo  本命盘出生信息
     * @param targetDate 查询日期（yyyy-MM-dd），null = 今天
     * @param windowDays 流运窗口天数
     * @return 流运计算结果
     */
    public TransitResponseDTO calculateTransit(BirthInfoDTO birthInfo, String targetDate, Integer windowDays) {
        log.info("Calling Python transit: targetDate={}, windowDays={}", targetDate, windowDays);
        try {
            com.fasterxml.jackson.databind.node.ObjectNode reqNode = objectMapper.createObjectNode();
            reqNode.set("birth_info", objectMapper.valueToTree(birthInfo));
            if (targetDate != null) {
                reqNode.put("target_date", targetDate);
            }
            if (windowDays != null) {
                reqNode.put("window_days", windowDays);
            }

            String requestBody = objectMapper.writeValueAsString(reqNode);
            String responseBody = post(PATH_TRANSIT, requestBody, TIMEOUT_TRANSIT);

            JsonNode root = objectMapper.readTree(responseBody);

            List<JsonNode> events = new ArrayList<>();
            if (root.has("events") && root.get("events").isArray()) {
                root.get("events").forEach(events::add);
            }

            return TransitResponseDTO.builder()
                    .events(events)
                    .summary(root.get("summary"))
                    .chart(root)
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Python transit calculation failed", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }

    // ─────────────────────── RAG ───────────────────────

    /**
     * 调用 Python RAG 服务检索占星知识
     *
     * @param query     语义检索 query（由星盘标签/关注焦点组合生成）
     * @param topK      返回条数，默认 5
     * @return RAG 检索结果原始文本（拼接成字符串供 Prompt 使用）
     */
    public String queryRag(String query, int topK) {
        log.debug("Calling Python RAG: query={}, topK={}", query, topK);
        try {
            com.fasterxml.jackson.databind.node.ObjectNode reqNode = objectMapper.createObjectNode();
            reqNode.put("query", query);
            reqNode.put("top_k", topK);

            String requestBody = objectMapper.writeValueAsString(reqNode);
            String responseBody = post(PATH_RAG, requestBody, TIMEOUT_RAG);

            JsonNode root = objectMapper.readTree(responseBody);

            // 将结果列表拼接为文本片段
            StringBuilder sb = new StringBuilder();
            if (root.has("results") && root.get("results").isArray()) {
                root.get("results").forEach(item -> {
                    String content = item.has("content") ? item.get("content").asText() : item.asText();
                    if (content != null && !content.isBlank()) {
                        sb.append(content).append("\n\n");
                    }
                });
            } else if (root.has("content")) {
                sb.append(root.get("content").asText());
            }

            String result = sb.toString().trim();
            log.debug("RAG result length: {}", result.length());
            return result;
        } catch (Exception e) {
            // RAG 失败为非致命错误，静默降级，不阻断星盘解读主流程
            // BusinessException（包括 Python 返回空体的情况）同样降级处理
            log.warn("Python RAG query failed (non-fatal, continue without RAG): {}", e.getMessage());
            return "";
        }
    }

    // ─────────────────────── HTTP 工具 ───────────────────────

    /**
     * 向 Python 服务发送 POST 请求，带超时控制
     *
     * <p>注意：若 Python 服务返回空体或 Mono.empty()，{@code block()} 返回 null。
     * 调用方需自行处理 null，此处统一转为空字符串抛出 BusinessException。</p>
     */
    private String post(String path, String body, Duration timeout) {
        String url = pythonBaseUrl + path;
        try {
            String responseBody = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(timeout)
                    .doOnError(e -> log.error("Python service error: url={}, error={}", url, e.getMessage()))
                    .block();
            // Python 服务不应返回空体；若出现则按错误处理
            if (responseBody == null || responseBody.isBlank()) {
                log.error("Python service returned empty response: url={}", url);
                throw new BusinessException(ResultCode.SYSTEM_ERROR);
            }
            return responseBody;
        } catch (BusinessException e) {
            throw e;
        } catch (WebClientResponseException e) {
            log.error("Python service HTTP error: url={}, status={}, body={}",
                    url, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                log.error("Python service timeout: url={}, timeout={}ms", url, timeout.toMillis());
            } else {
                log.error("Python service call failed: url={}", url, e);
            }
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }
}

