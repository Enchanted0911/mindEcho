package com.mindecho.module.astrology.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.time.ZoneId;
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
 * <p>Python 服务接口规范：
 * <ul>
 *   <li>/internal/natal 接受 {datetime, lat, lng, timezone}，返回完整 NatalChart</li>
 *   <li>/internal/synastry 接受 {chart_a, chart_b}（两个 NatalChart），返回 SynastryResult</li>
 *   <li>/internal/transit 接受 {natal_chart, transit_time}（NatalChart + ISO-8601 时间），返回 TransitResult</li>
 * </ul>
 *
 * <p>超时策略（按 PRD 要求）：
 * <ul>
 *   <li>natal：3s</li>
 *   <li>transit：6s（内部含一次 natal 调用）</li>
 *   <li>synastry：10s（内部含两次 natal 调用）</li>
 *   <li>rag：5s</li>
 * </ul>
 *
 * @author mindecho
 */
@Slf4j
@Component
public class AstrologyPythonClient {

    private static final Duration TIMEOUT_NATAL    = Duration.ofSeconds(30);
    private static final Duration TIMEOUT_SYNASTRY = Duration.ofSeconds(60);
    private static final Duration TIMEOUT_TRANSIT  = Duration.ofSeconds(45);
    private static final Duration TIMEOUT_RAG      = Duration.ofSeconds(60);

    private static final String PATH_NATAL    = "/internal/natal";
    private static final String PATH_SYNASTRY = "/internal/synastry";
    private static final String PATH_TRANSIT  = "/internal/transit";
    private static final String PATH_RAG      = "/internal/rag/query";

    /** 默认时区（用于无经纬度、无时区时的兜底） */
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    // ── JSON 字段名常量 ──────────────────────────────────────────────────────
    private static final String FIELD_ASPECTS        = "aspects";
    private static final String FIELD_EVENTS         = "events";
    private static final String FIELD_RESULTS        = "results";
    private static final String FIELD_CHUNKS         = "chunks";
    private static final String FIELD_CONTENT        = "content";
    private static final String FIELD_THEMES         = "themes";
    private static final String FIELD_DOMINANT_THEMES = "dominant_themes";
    private static final String FIELD_RELATIONSHIP_MODEL = "relationship_model";
    /** Python 有时以驼峰形式返回 relationship_model */
    private static final String FIELD_RELATIONSHIP_MODEL_CAMEL = "relationshipModel";
    private static final String FIELD_SUMMARY        = "summary";
    private static final String FIELD_PLANETS        = "planets";
    private static final String FIELD_ANGLES         = "angles";
    private static final String FIELD_ASCENDANT      = "ascendant";
    private static final String FIELD_METADATA       = "metadata";
    private static final String TIMEOUT_KEYWORD      = "timeout";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${astrology.python-service.base-url:http://localhost:8000}")
    private String pythonBaseUrl;

    public AstrologyPythonClient(WebClient.Builder webClientBuilder,
                                  @Qualifier("webObjectMapper") ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    // ─────────────────────── 本命盘 ───────────────────────

    /**
     * 调用 Python 服务计算本命盘。
     *
     * <p>Python /internal/natal 接口要求字段：
     * <pre>
     *   datetime : 出生本地时间，ISO-8601（如 1998-08-12T20:30:00）
     *   lat      : 纬度（-90~90）
     *   lng      : 经度（-180~180）
     *   timezone : IANA 时区名称（如 Asia/Shanghai）
     * </pre>
     *
     * @param birthInfo 前端提交的出生信息（year/month/day/hour/minute/city/latitude/longitude/timezone）
     * @return 本命盘计算结果（chart 字段即完整 NatalChart JSON，summary 从中提取）
     */
    public NatalChartResponseDTO calculateNatal(BirthInfoDTO birthInfo) {
        log.info("Calling Python natal: city={}, date={}-{}-{} {}:{}",
                birthInfo.getCity(), birthInfo.getYear(), birthInfo.getMonth(),
                birthInfo.getDay(), birthInfo.getHour(), birthInfo.getMinute());
        try {
            ObjectNode reqNode = buildNatalRequest(birthInfo);
            String requestBody = objectMapper.writeValueAsString(reqNode);
            String responseBody = post(PATH_NATAL, requestBody, TIMEOUT_NATAL);

            // Python 直接返回完整 NatalChart（planets/angles/nodes/houses/aspects/metadata）
            JsonNode chart = objectMapper.readTree(responseBody);
            return NatalChartResponseDTO.builder()
                    .chart(chart)
                    .summary(extractNatalSummary(chart))
                    .savedToProfile(false)
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Python natal calculation failed", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 内部方法：直接返回 NatalChart 的原始 JsonNode（供 transit / synastry 复用）
     */
    private JsonNode calculateNatalChart(BirthInfoDTO birthInfo) {
        try {
            ObjectNode reqNode = buildNatalRequest(birthInfo);
            String requestBody = objectMapper.writeValueAsString(reqNode);
            String responseBody = post(PATH_NATAL, requestBody, TIMEOUT_NATAL);
            return objectMapper.readTree(responseBody);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Python natal chart (internal) calculation failed", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }

    // ─────────────────────── 和盘 ───────────────────────

    /**
     * 调用 Python 服务计算和盘。
     *
     * <p>流程：
     * <ol>
     *   <li>分别调用 /internal/natal 获取双方的 NatalChart</li>
     *   <li>将两个 NatalChart 作为 chart_a / chart_b 发送到 /internal/synastry</li>
     * </ol>
     *
     * @param selfBirth    本人出生信息
     * @param partnerBirth 对方出生信息
     * @return 和盘计算结果
     */
    public SynastryResponseDTO calculateSynastry(BirthInfoDTO selfBirth, BirthInfoDTO partnerBirth) {
        log.info("Calling Python synastry: self={}, partner={}", selfBirth.getCity(), partnerBirth.getCity());
        try {
            // Step 1：分别获取双方本命盘
            JsonNode chartA = calculateNatalChart(selfBirth);
            JsonNode chartB = calculateNatalChart(partnerBirth);

            // Step 2：发送和盘请求
            ObjectNode reqNode = objectMapper.createObjectNode();
            reqNode.set("chart_a", chartA);
            reqNode.set("chart_b", chartB);

            String requestBody = objectMapper.writeValueAsString(reqNode);
            String responseBody = post(PATH_SYNASTRY, requestBody, TIMEOUT_SYNASTRY);

            JsonNode root = objectMapper.readTree(responseBody);

            // Python /internal/synastry 返回结构为 { "<pairHash>": { aspects, relationshipModel/relationship_model, ... } }
            // 需要先取第一个 value 节点（即 pairHash 对应的数据），再解析内部字段
            JsonNode data = root;
            if (root.isObject() && root.size() == 1) {
                JsonNode firstValue = root.fields().next().getValue();
                // 若第一层的 value 本身包含 aspects / relationship_model / relationshipModel 等字段，则说明是嵌套结构
                if (firstValue.isObject() && (firstValue.has(FIELD_ASPECTS)
                        || firstValue.has(FIELD_RELATIONSHIP_MODEL)
                        || firstValue.has(FIELD_RELATIONSHIP_MODEL_CAMEL))) {
                    data = firstValue;
                    log.debug("Synastry: detected pairHash-wrapped response, unwrapped to inner data node");
                }
            }

            List<JsonNode> aspects = new ArrayList<>();
            if (data.has(FIELD_ASPECTS) && data.get(FIELD_ASPECTS).isArray()) {
                data.get(FIELD_ASPECTS).forEach(aspects::add);
            }

            List<String> themes = new ArrayList<>();
            if (data.has(FIELD_DOMINANT_THEMES) && data.get(FIELD_DOMINANT_THEMES).isArray()) {
                data.get(FIELD_DOMINANT_THEMES).forEach(n -> themes.add(n.asText()));
            } else if (data.has(FIELD_THEMES) && data.get(FIELD_THEMES).isArray()) {
                data.get(FIELD_THEMES).forEach(n -> themes.add(n.asText()));
            }

            // 兼容 relationship_model（下划线）和 relationshipModel（驼峰）两种字段名
            JsonNode relationshipModelNode = data.has(FIELD_RELATIONSHIP_MODEL)
                    ? data.get(FIELD_RELATIONSHIP_MODEL)
                    : data.get(FIELD_RELATIONSHIP_MODEL_CAMEL);

            return SynastryResponseDTO.builder()
                    .relationshipModel(relationshipModelNode)
                    .aspects(aspects)
                    .themes(themes)
                    .chart(data)
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
     * 调用 Python 服务计算流运。
     *
     * <p>流程：
     * <ol>
     *   <li>调用 /internal/natal 获取本命盘 NatalChart</li>
     *   <li>将 NatalChart + transit_time 发送到 /internal/transit</li>
     * </ol>
     *
     * @param birthInfo  出生信息（用于先获取本命盘）
     * @param targetDate 查询日期（yyyy-MM-dd），null = 今天
     * @param windowDays 流运窗口天数（当前 Python 接口暂不使用，预留）
     * @return 流运计算结果
     */
    public TransitResponseDTO calculateTransit(BirthInfoDTO birthInfo, String targetDate, Integer windowDays) {
        log.info("Calling Python transit: city={}, targetDate={}", birthInfo.getCity(), targetDate);
        try {
            // Step 1：获取本命盘
            JsonNode natalChart = calculateNatalChart(birthInfo);

            // Step 2：构造 transit_time（ISO-8601 格式，使用目标日期正午时刻）
            String transitTime = buildTransitTime(targetDate);

            // Step 3：发送流运请求
            ObjectNode reqNode = objectMapper.createObjectNode();
            reqNode.set("natal_chart", natalChart);
            reqNode.put("transit_time", transitTime);

            String requestBody = objectMapper.writeValueAsString(reqNode);
            String responseBody = post(PATH_TRANSIT, requestBody, TIMEOUT_TRANSIT);

            JsonNode root = objectMapper.readTree(responseBody);

            // 解析顶层 date 字段（Python 返回如 "2026-06-02"）
            String dateField = root.has("date") ? root.get("date").asText(null) : null;

            List<JsonNode> events = new ArrayList<>();
            if (root.has(FIELD_EVENTS) && root.get(FIELD_EVENTS).isArray()) {
                root.get(FIELD_EVENTS).forEach(events::add);
            }

            // summary 字段：Python 返回 { emotional_state, energy_level, life_focus }
            JsonNode summaryNode = root.has(FIELD_SUMMARY) ? root.get(FIELD_SUMMARY) : null;

            return TransitResponseDTO.builder()
                    .date(dateField)
                    .events(events)
                    .summary(summaryNode)
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
     * 调用 Python RAG 服务检索占星知识。
     *
     * <p>Python 返回结构（RAGResult）：
     * <pre>
     * {
     *   "query":  "...",
     *   "chunks": [ { "content": "...", ... }, ... ],
     *   "debug":  { "bm25_hits": N, "vector_hits": N, "rerank_enabled": true, "cutoff": 0.7 }
     * }
     * </pre>
     *
     * @param query 语义检索 query（由星盘标签/关注焦点组合生成）
     * @param topK  返回条数，默认 5
     * @return RAG 检索结果原始文本（各 chunk 的 content 拼接，供 Prompt 使用）
     */
    public String queryRag(String query, int topK) {
        log.debug("Calling Python RAG: query={}, topK={}", query, topK);
        try {
            ObjectNode reqNode = objectMapper.createObjectNode();
            reqNode.put("query", query);
            reqNode.put("top_k", topK);

            String requestBody = objectMapper.writeValueAsString(reqNode);
            String responseBody = post(PATH_RAG, requestBody, TIMEOUT_RAG);

            JsonNode root = objectMapper.readTree(responseBody);

            StringBuilder sb = new StringBuilder();

            // Python RAGResult 的内容在 chunks 字段
            if (root.has(FIELD_CHUNKS) && root.get(FIELD_CHUNKS).isArray()) {
                root.get(FIELD_CHUNKS).forEach(chunk -> {
                    String content = chunk.has(FIELD_CONTENT) ? chunk.get(FIELD_CONTENT).asText("") : "";
                    if (!content.isBlank()) {
                        sb.append(content).append("\n\n");
                    }
                });
            }

            // 记录 debug 信息（如有）
            if (root.has("debug") && log.isDebugEnabled()) {
                JsonNode debug = root.get("debug");
                log.debug("RAG debug: bm25_hits={}, vector_hits={}, rerank_enabled={}, cutoff={}",
                        debug.path("bm25_hits").asInt(),
                        debug.path("vector_hits").asInt(),
                        debug.path("rerank_enabled").asBoolean(),
                        debug.path("cutoff").asDouble());
            }

            String result = sb.toString().trim();
            log.debug("RAG result length: {}", result.length());
            return result;
        } catch (Exception e) {
            // RAG 失败为非致命错误，静默降级，不阻断星盘解读主流程
            log.warn("Python RAG query failed (non-fatal, continue without RAG): {}", e.getMessage());
            return "";
        }
    }

    // ─────────────────────── 构建工具方法 ───────────────────────

    /**
     * 将 BirthInfoDTO 转换为 Python /internal/natal 所需的请求体：
     * <pre>
     *   datetime : "yyyy-MM-ddTHH:mm:ss"
     *   lat      : 纬度
     *   lng      : 经度
     *   timezone : "Asia/Shanghai"（IANA 格式）
     * </pre>
     */
    private ObjectNode buildNatalRequest(BirthInfoDTO info) {
        ObjectNode node = objectMapper.createObjectNode();

        // 1. datetime：ISO-8601 本地时间
        String datetime = String.format("%04d-%02d-%02dT%02d:%02d:00",
                info.getYear(), info.getMonth(), info.getDay(),
                info.getHour(), info.getMinute());
        node.put("datetime", datetime);

        // 2. lat / lng：优先使用前端传入的经纬度
        double lat;
        double lng;
        if (info.getLatitude() != null && info.getLongitude() != null) {
            lat = info.getLatitude();
            lng = info.getLongitude();
        } else {
            // 无经纬度时兜底使用北京坐标（前端应确保传入 lat/lng）
            log.warn("No latitude/longitude provided for city={}, falling back to Beijing", info.getCity());
            lat = 39.9042;
            lng = 116.4074;
        }
        node.put("lat", lat);
        node.put("lng", lng);

        // 3. timezone：优先使用前端传入的 IANA 时区，否则根据经度推断
        String timezone = resolveTimezone(info.getTimezone(), lng);
        node.put("timezone", timezone);

        return node;
    }

    /**
     * 推断 IANA 时区名称。
     *
     * <p>优先级：
     * <ol>
     *   <li>前端显式传入的 timezone（需为 IANA 格式）</li>
     *   <li>根据经度估算（中国大陆统一使用 Asia/Shanghai）</li>
     *   <li>兜底使用 Asia/Shanghai</li>
     * </ol>
     */
    private String resolveTimezone(String explicitTimezone, double lng) {
        if (explicitTimezone != null && !explicitTimezone.isBlank()) {
            // 验证是否为有效 IANA 时区
            try {
                ZoneId.of(explicitTimezone);
                return explicitTimezone;
            } catch (Exception e) {
                log.warn("Invalid IANA timezone '{}', will auto-detect", explicitTimezone);
            }
        }
        // 根据经度简单推断（主要场景：中国用户）
        // 日本优先，避免被中国大陆范围覆盖（日本经度 130°~145°，与中国东北部重叠）
        if (lng >= 130.0 && lng < 145.0) {
            return "Asia/Tokyo";
        } else if (lng >= 73.0 && lng < 130.0) {
            // 中国大陆（东经 73°~130°，统一使用 Asia/Shanghai，包含东南亚部分兜底）
            return "Asia/Shanghai";
        } else if (lng >= 60.0 && lng < 73.0) {
            // 南亚（印度、巴基斯坦等）
            return "Asia/Kolkata";
        } else if (lng >= 15.0 && lng < 60.0) {
            // 中欧/东欧/中东
            return "Europe/Berlin";
        } else if (lng >= 0.0 && lng < 15.0) {
            // 西欧（英国等）
            return "Europe/London";
        } else if (lng >= -75.0 && lng < -30.0) {
            // 美国东部
            return "America/New_York";
        } else if (lng >= -105.0 && lng < -75.0) {
            // 美国中部/山地
            return "America/Chicago";
        } else if (lng >= -130.0 && lng < -105.0) {
            // 美国西部
            return "America/Los_Angeles";
        }
        return DEFAULT_TIMEZONE;
    }

    /**
     * 构建流运查询时间（transit_time），ISO-8601 格式。
     *
     * <p>使用目标日期的正午 12:00:00（UTC），以确保与当天流运相位对应。
     *
     * @param targetDate yyyy-MM-dd 格式，null = 今天
     */
    private String buildTransitTime(String targetDate) {
        String date = (targetDate != null && !targetDate.isBlank())
                ? targetDate
                : java.time.LocalDate.now().toString();
        // 使用 UTC 正午时刻作为流运查询时间点
        return date + "T12:00:00Z";
    }

    /**
     * 从 NatalChart 中提取摘要信息（太阳/月亮/上升星座），供前端快速展示。
     */
    private JsonNode extractNatalSummary(JsonNode chart) {
        try {
            ObjectNode summary = objectMapper.createObjectNode();
            // planets 字段：sun / moon
            JsonNode planets = chart.get("planets");
            if (planets != null) {
                if (planets.has("sun")) {
                    summary.set("sun", planets.get("sun"));
                }
                if (planets.has("moon")) {
                    summary.set("moon", planets.get("moon"));
                }
            }
            // angles 字段：ascendant（上升点）
            JsonNode angles = chart.get("angles");
            if (angles != null && angles.has("ascendant")) {
                summary.set("ascendant", angles.get("ascendant"));
            }
            // metadata
            if (chart.has("metadata")) {
                summary.set("metadata", chart.get("metadata"));
            }
            return summary.isEmpty() ? null : summary;
        } catch (Exception e) {
            log.warn("Failed to extract natal summary: {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────── HTTP 工具 ───────────────────────

    /**
     * 向 Python 服务发送 POST 请求，带超时控制
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
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof java.util.concurrent.TimeoutException
                    || (e.getMessage() != null && e.getMessage().contains("TimeoutException"))) {
                log.error("Python service timeout: url={}, timeout={}ms — 占星计算耗时超过阈值，请适当调大超时",
                        url, timeout.toMillis());
            } else {
                log.error("Python service call failed: url={}", url, e);
            }
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }
}

