package com.mindecho.module.astrology.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import com.mindecho.module.astrology.client.AstrologyPythonClient;
import com.mindecho.module.astrology.dto.AstrologyInterpretRequestDTO;
import com.mindecho.module.astrology.dto.AstrologyInterpretResponseDTO;
import com.mindecho.module.astrology.dto.SynastryInterpretRequestDTO;
import com.mindecho.module.astrology.dto.TransitInterpretRequestDTO;
import com.mindecho.module.astrology.entity.UserAstrology;
import com.mindecho.module.astrology.memory.AstrologyMemoryService;
import com.mindecho.module.astrology.prompt.AstrologerPromptBuilder;
import com.mindecho.module.billing.entity.AiUsageRecord;
import com.mindecho.module.billing.service.BillingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * AI 占星解读服务（Java AI Emotional Astrology Brain）
 *
 * <p>核心功能：将 Python 计算的星盘数据转化为温柔、有洞察力的 AI 占星解读。
 *
 * <p>处理流程：
 * <pre>
 * 星盘数据（从 user_astrology 表读取，通过 UserAstrologyService）
 *   ↓
 * Python RAG 检索（调用 /internal/rag/query，带 6h 缓存）
 *   ↓
 * 用户 Memory 检索（按解读类型差异化策略，仅读取不写入）
 *   ↓
 * Prompt 组装（AstrologerPromptBuilder）
 *   ↓
 * 预扣积分（BillingService）
 *   ↓
 * LLM 调用（DeepSeek）
 *   ↓
 * AI 占星解读文本
 *   ↓
 * 积分结算（BillingService）
 *   ↓
 * 异步保存解读文本到 user_astrology（*_interpretation 字段）
 * </pre>
 *
 * <p>RAG 缓存（PRD §7.2）：{@code rag:{queryHash}} TTL = 6h
 */
@Slf4j
@Service
public class AstrologyAiService {

    private static final long TTL_RAG_HOURS = 6L;
    private static final int RAG_TOP_K = 5;

    private final AstrologyPythonClient pythonClient;
    private final AstrologyMemoryService astrologyMemoryService;
    private final AstrologerPromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BillingService billingService;
    private final UserAstrologyService userAstrologyService;
    private final ObjectMapper objectMapper;

    @Value("${astrology.rag.enabled:true}")
    private boolean ragEnabled;

    public AstrologyAiService(AstrologyPythonClient pythonClient,
                              AstrologyMemoryService astrologyMemoryService,
                              AstrologerPromptBuilder promptBuilder,
                              @Qualifier("astrologyChatClient") ChatClient chatClient,
                              RedisTemplate<String, Object> redisTemplate,
                              BillingService billingService,
                              UserAstrologyService userAstrologyService,
                              @Qualifier("webObjectMapper") ObjectMapper objectMapper) {
        this.pythonClient = pythonClient;
        this.astrologyMemoryService = astrologyMemoryService;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClient;
        this.redisTemplate = redisTemplate;
        this.billingService = billingService;
        this.userAstrologyService = userAstrologyService;
        this.objectMapper = objectMapper;
    }

    // ─────────────────────── 单盘解读 ─────────────────────────────────────

    /**
     * 单星盘（本命盘）AI 解读
     *
     * <p>chart 数据从 user_astrology 表 natal_chart_data 字段读取，前端无需传 chart。
     * 前置条件：user_astrology 表中已有本命盘数据（natal_chart_data 字段不为空）。
     * 解读完成后将结果异步写入 natal_interpretation 字段。
     *
     * @param userId  当前用户 ID
     * @param request 解读请求（只含 focus、tone，chart 由后端读取）
     * @return AI 占星解读结果
     */
    public AstrologyInterpretResponseDTO interpretNatal(UUID userId, AstrologyInterpretRequestDTO request) {
        log.info("Interpreting natal chart: userId={}, focus={}", userId, request.getFocus());

        // Step 0: 从 user_astrology 表读取本命盘 chart 数据
        UserAstrology astrology = loadAstrology(userId);
        JsonNode chartNode = parseNatalChart(userId, astrology);

        // Step 1: RAG 检索
        String ragQuery = buildNatalRagQuery(chartNode, request.getFocus());
        String ragContent = queryRagWithCache(ragQuery);
        boolean ragFused = StringUtils.hasText(ragContent);

        // Step 2: Memory 检索（pgvector 语义检索，query 使用 RAG query 兜底）
        List<String> memories = astrologyMemoryService.recallForNatal(userId, ragQuery);
        boolean memoryFused = !memories.isEmpty();

        // Step 3: 构建 Prompt
        String systemPrompt = promptBuilder.buildNatalPrompt(
                chartNode, ragContent, memories,
                request.getFocus(), request.getTone());
        String userInstruction = buildUserInstruction("NATAL", request.getFocus(), request.getExtraContext());

        // Step 4: 预扣积分
        int estimatedTokens = estimatePromptTokens(systemPrompt, userInstruction);
        AiUsageRecord usageRecord = initBillingQuietly(userId, "ASTROLOGY_NATAL", estimatedTokens);

        // Step 5: 调用 LLM（含 token 统计）
        LlmResult result = callLlmWithUsage(systemPrompt, userInstruction);

        // Step 6: 积分结算
        settleBillingQuietly(usageRecord, userId, result);

        // Step 7: 异步保存解读文本到 user_astrology.natal_interpretation（通过外部 Bean 保证 @Async 生效）
        userAstrologyService.saveNatalInterpretationAsync(userId, result.content);

        return AstrologyInterpretResponseDTO.builder()
                .interpretation(result.content)
                .focus(request.getFocus())
                .interpretType("NATAL")
                .memoryFused(memoryFused)
                .ragFused(ragFused)
                .build();
    }

    // ─────────────────────── 和盘解读 ─────────────────────────────────────

    /**
     * 和盘 AI 解读（从 user_astrology 表读取 chart）
     *
     * <p>chart 数据从 user_astrology 表 synastry_chart_data 字段读取，前端只需传 relationshipType 和 focus。
     * 前置条件：user_astrology 表中已有和盘数据（synastry_chart_data 字段不为空）。
     * 解读完成后将结果异步写入 synastry_interpretation 字段。
     *
     * @param userId  当前用户 ID
     * @param request 解读请求（只含 relationshipType、focus、tone）
     * @return AI 占星解读结果
     */
    public AstrologyInterpretResponseDTO interpretSynastry(UUID userId, SynastryInterpretRequestDTO request) {
        log.info("Interpreting synastry chart: userId={}, focus={}", userId, request.getFocus());

        // Step 0: 从 user_astrology 表读取和盘 chart 数据
        UserAstrology astrology = loadAstrology(userId);
        JsonNode chartNode = parseSynastryChart(userId, astrology);

        // 从 user_astrology 表读取对方昵称
        String partnerName = StringUtils.hasText(astrology.getSynastryPartnerName())
                ? astrology.getSynastryPartnerName() : "Ta";
        String relationshipType = StringUtils.hasText(request.getRelationshipType())
                ? request.getRelationshipType() : "romantic";

        String ragQuery = buildSynastryRagQuery(chartNode, request.getFocus());
        String ragContent = queryRagWithCache(ragQuery);
        boolean ragFused = StringUtils.hasText(ragContent);

        List<String> memories = astrologyMemoryService.recallForSynastry(userId, ragQuery);
        boolean memoryFused = !memories.isEmpty();

        String systemPrompt = promptBuilder.buildSynastryPrompt(
                chartNode, ragContent, memories,
                partnerName, relationshipType,
                request.getFocus(), request.getTone());
        String userInstruction = buildUserInstruction("SYNASTRY", request.getFocus(),
                partnerName + "|" + relationshipType);

        int estimatedTokens = estimatePromptTokens(systemPrompt, userInstruction);
        AiUsageRecord usageRecord = initBillingQuietly(userId, "ASTROLOGY_SYNASTRY", estimatedTokens);

        LlmResult result = callLlmWithUsage(systemPrompt, userInstruction);

        settleBillingQuietly(usageRecord, userId, result);

        // 异步保存解读文本到 user_astrology.synastry_interpretation（通过外部 Bean 保证 @Async 生效）
        userAstrologyService.saveSynastryInterpretationAsync(userId, result.content);

        // 异步写入关系记忆（供主聊天感知用户感情状态）
        astrologyMemoryService.extractAndSaveRelationshipFromInterpretAsync(userId, partnerName, relationshipType, result.content);

        return AstrologyInterpretResponseDTO.builder()
                .interpretation(result.content)
                .focus(request.getFocus())
                .interpretType("SYNASTRY")
                .memoryFused(memoryFused)
                .ragFused(ragFused)
                .build();
    }

    // ─────────────────────── 流运解读 ─────────────────────────────────────

    /**
     * 流运 AI 解读（从 user_astrology 表读取 chart）
     *
     * <p>chart 数据从 user_astrology 表 transit_chart_data 字段读取，前端只需传 windowDays 和 focus。
     * 前置条件：user_astrology 表中已有流运数据（transit_chart_data 字段不为空）。
     * 解读完成后将结果异步写入 transit_interpretation 字段。
     *
     * @param userId  当前用户 ID
     * @param request 解读请求（只含 windowDays、focus、tone）
     * @return AI 占星解读结果
     */
    public AstrologyInterpretResponseDTO interpretTransit(UUID userId, TransitInterpretRequestDTO request) {
        log.info("Interpreting transit chart: userId={}, focus={}", userId, request.getFocus());

        // Step 0: 从 user_astrology 表读取流运 chart 数据
        UserAstrology astrology = loadAstrology(userId);
        JsonNode chartNode = parseTransitChart(userId, astrology);

        // 获取缓存的目标日期
        String targetDate = StringUtils.hasText(astrology.getTransitTargetDate())
                ? astrology.getTransitTargetDate() : java.time.LocalDate.now().toString();

        String ragQuery = buildTransitRagQuery(chartNode, request.getFocus());
        String ragContent = queryRagWithCache(ragQuery);
        boolean ragFused = StringUtils.hasText(ragContent);

        List<String> memories = astrologyMemoryService.recallForTransit(userId, ragQuery);
        boolean memoryFused = !memories.isEmpty();

        String systemPrompt = promptBuilder.buildTransitPrompt(
                chartNode, ragContent, memories,
                targetDate, request.getFocus(), request.getTone());
        String userInstruction = buildUserInstruction("TRANSIT", request.getFocus(), targetDate);

        int estimatedTokens = estimatePromptTokens(systemPrompt, userInstruction);
        AiUsageRecord usageRecord = initBillingQuietly(userId, "ASTROLOGY_TRANSIT", estimatedTokens);

        LlmResult result = callLlmWithUsage(systemPrompt, userInstruction);

        settleBillingQuietly(usageRecord, userId, result);

        // 异步保存解读文本到 user_astrology.transit_interpretation（通过外部 Bean 保证 @Async 生效）
        userAstrologyService.saveTransitInterpretationAsync(userId, result.content);

        return AstrologyInterpretResponseDTO.builder()
                .interpretation(result.content)
                .focus(request.getFocus())
                .interpretType("TRANSIT")
                .memoryFused(memoryFused)
                .ragFused(ragFused)
                .build();
    }

    // ─────────────────────── LLM 调用（含 usage 统计） ──────────────────────

    /**
     * 调用 DeepSeek LLM 并返回文本 + token 使用量
     */
    private LlmResult callLlmWithUsage(String systemPrompt, String userMessage) {
        try {
            ChatResponse chatResponse = chatClient.prompt()
                    .messages(
                            new SystemMessage(systemPrompt),
                            new UserMessage(userMessage)
                    )
                    .call()
                    .chatResponse();

            String content = "";
            if (chatResponse != null && chatResponse.getResult() != null
                    && chatResponse.getResult().getOutput() != null) {
                content = chatResponse.getResult().getOutput().getText();
                if (content == null) content = "";
                content = content.trim();
            }
            if (content.isEmpty()) {
                content = "暂时无法生成解读，请稍后再试。";
            }

            // 提取 token usage
            int promptTokens = 0;
            int completionTokens = 0;
            try {
                if (chatResponse != null && chatResponse.getMetadata() != null
                        && chatResponse.getMetadata().getUsage() != null) {
                    var usage = chatResponse.getMetadata().getUsage();
                    if (usage.getPromptTokens() != null) {
                        promptTokens = usage.getPromptTokens().intValue();
                    }
                    if (usage.getCompletionTokens() != null) {
                        completionTokens = usage.getCompletionTokens().intValue();
                    }
                }
            } catch (Exception e) {
                log.debug("Token usage extraction failed: {}", e.getMessage());
            }

            return new LlmResult(content, promptTokens, completionTokens, false);

        } catch (Exception e) {
            log.error("LLM call failed for astrology interpretation", e);
            return new LlmResult("星屿正在调整状态，请稍后重试 ✨", 0, 0, true);
        }
    }

    // ─────────────────────── 计费辅助方法 ─────────────────────────────────

    /**
     * 初始化计费（安静模式：异常时不抛出，仅记录日志）
     * 占星场景下计费失败不应阻断业务
     */
    private AiUsageRecord initBillingQuietly(UUID userId, String businessType, int estimatedTokens) {
        try {
            return billingService.initUsageAndPreDeduct(userId, null, businessType, estimatedTokens);
        } catch (com.mindecho.common.exception.BusinessException e) {
            // 积分不足：占星服务选择抛出（重要功能需付费）
            throw e;
        } catch (Exception e) {
            log.error("Billing init failed for astrology: userId={}, type={}", userId, businessType, e);
            return null;
        }
    }

    /**
     * 结算计费（安静模式）
     */
    private void settleBillingQuietly(AiUsageRecord usageRecord, UUID userId, LlmResult result) {
        if (usageRecord == null) return;
        try {
            if (result.failed) {
                billingService.failAndRefund(usageRecord.getId(), userId);
            } else {
                int promptTokens = result.promptTokens > 0 ? result.promptTokens
                        : (usageRecord.getContextTokens() != null ? usageRecord.getContextTokens() : 500);
                int completionTokens = result.completionTokens > 0 ? result.completionTokens
                        : result.content.length() / 4;
                billingService.settleUsage(usageRecord.getId(), userId, promptTokens, completionTokens);
            }
        } catch (Exception e) {
            log.error("Billing settle failed for astrology: usageId={}", usageRecord.getId(), e);
        }
    }

    /**
     * 估算 prompt token 数（粗算）
     */
    private int estimatePromptTokens(String systemPrompt, String userMessage) {
        int totalChars = (systemPrompt != null ? systemPrompt.length() : 0)
                + (userMessage != null ? userMessage.length() : 0);
        return Math.max(totalChars / 3, 200);
    }

    /**
     * LLM 调用结果封装
     */
    private record LlmResult(String content, int promptTokens, int completionTokens, boolean failed) {
    }

    // ─────────────────────── RAG 缓存 ─────────────────────────────────────

    /**
     * 带 Redis 缓存的 RAG 检索（TTL = 6h）
     */
    private String queryRagWithCache(String query) {
        if (!ragEnabled || !StringUtils.hasText(query)) {
            return "";
        }

        // 使用 SHA-256 前 8 字节（64 bit）作为 RAG 缓存 key
        String queryHash;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(query.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            queryHash = sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            queryHash = String.valueOf(Integer.toUnsignedLong(query.hashCode()));
        }
        String cacheKey = AstrologyGatewayService.buildRagCacheKey(queryHash);

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof String cachedStr && !cachedStr.isBlank()) {
                log.debug("RAG cache hit: query={}", query.substring(0, Math.min(query.length(), 30)));
                return cachedStr;
            }
        } catch (Exception e) {
            log.warn("RAG cache read failed: {}", e.getMessage());
        }

        String result = pythonClient.queryRag(query, RAG_TOP_K);

        if (StringUtils.hasText(result)) {
            try {
                redisTemplate.opsForValue().set(cacheKey, result, TTL_RAG_HOURS, TimeUnit.HOURS);
            } catch (Exception e) {
                log.warn("RAG cache write failed: {}", e.getMessage());
            }
        }
        return result;
    }

    // ─────────────────────── DB Chart 加载辅助方法 ─────────────────────────

    /**
     * 加载用户 UserAstrology 记录（若不存在则抛出 USER_NOT_FOUND）
     */
    private UserAstrology loadAstrology(UUID userId) {
        UserAstrology astrology = userAstrologyService.getOrInitAstrology(userId);
        if (astrology == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return astrology;
    }

    /**
     * 从 UserAstrology 解析本命盘 chart 数据
     */
    private JsonNode parseNatalChart(UUID userId, UserAstrology astrology) {
        if (!StringUtils.hasText(astrology.getNatalChartData())) {
            throw new BusinessException(ResultCode.NATAL_CHART_NOT_FOUND);
        }
        try {
            com.fasterxml.jackson.databind.node.ObjectNode wrapper =
                    objectMapper.readValue(astrology.getNatalChartData(),
                            com.fasterxml.jackson.databind.node.ObjectNode.class);
            if (wrapper.has("chart") && !wrapper.get("chart").isNull()) {
                return wrapper.get("chart");
            }
            return wrapper;
        } catch (Exception e) {
            log.warn("Failed to parse natal_chart_data for interpret: userId={}", userId);
            throw new BusinessException(ResultCode.NATAL_CHART_NOT_FOUND);
        }
    }

    /**
     * 从 UserAstrology 解析和盘 chart 数据
     *
     * <p>synastry_chart_data 格式为按 pairHash 索引的 LinkedHashMap（最多保留 5 条，按插入顺序），
     * 最后插入的即最新的和盘数据。通过取最后一个 Entry 而非 stream().reduce() 来获取最新数据，
     * 更直观且不依赖 stream 语义。
     */
    private JsonNode parseSynastryChart(UUID userId, UserAstrology astrology) {
        if (!StringUtils.hasText(astrology.getSynastryChartData())) {
            throw new BusinessException(ResultCode.SYNASTRY_CHART_NOT_FOUND);
        }
        try {
            java.util.LinkedHashMap<String, Object> synastryMap = objectMapper.readValue(
                    astrology.getSynastryChartData(),
                    new com.fasterxml.jackson.core.type.TypeReference<
                            java.util.LinkedHashMap<String, Object>>() {});
            if (synastryMap.isEmpty()) {
                throw new BusinessException(ResultCode.SYNASTRY_CHART_NOT_FOUND);
            }
            // 取 LinkedHashMap 中最后一个 Entry（即最新插入的和盘记录）
            java.util.Map.Entry<String, Object> lastEntry = null;
            for (java.util.Map.Entry<String, Object> e : synastryMap.entrySet()) {
                lastEntry = e;
            }
            if (lastEntry == null) {
                throw new BusinessException(ResultCode.SYNASTRY_CHART_NOT_FOUND);
            }
            JsonNode entryNode = objectMapper.valueToTree(lastEntry.getValue());
            if (entryNode.has("chart") && !entryNode.get("chart").isNull()) {
                return entryNode.get("chart");
            }
            return entryNode;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse synastry_chart_data for interpret: userId={}", userId);
            throw new BusinessException(ResultCode.SYNASTRY_CHART_NOT_FOUND);
        }
    }

    /**
     * 从 UserAstrology 解析流运 chart 数据
     *
     * <p>transit_chart_data 的存储格式为 {@code {date, events, summary}}（由 AstrologyGatewayService 写入），
     * 不含独立 chart 字段。此方法直接返回整个 wrapper 节点，供 AI 解读使用。
     * events 字段包含所有流运相位事件，summary 包含整体能量摘要，二者共同构成流运解读的上下文。
     */
    private JsonNode parseTransitChart(UUID userId, UserAstrology astrology) {
        if (!StringUtils.hasText(astrology.getTransitChartData())) {
            throw new BusinessException(ResultCode.TRANSIT_CHART_NOT_FOUND);
        }
        try {
            com.fasterxml.jackson.databind.node.ObjectNode wrapper =
                    objectMapper.readValue(astrology.getTransitChartData(),
                            com.fasterxml.jackson.databind.node.ObjectNode.class);
            // transit_chart_data 格式: {date, events, summary}，不含独立 chart 字段
            // 直接返回整个 wrapper，让 buildTransitRagQuery / promptBuilder 从 events/summary 提取关键词
            // 若 events 和 summary 均为空，视为无有效流运数据
            boolean hasEvents = wrapper.has("events") && wrapper.get("events").isArray()
                    && wrapper.get("events").size() > 0;
            boolean hasSummary = wrapper.has("summary") && !wrapper.get("summary").isNull();
            if (!hasEvents && !hasSummary) {
                throw new BusinessException(ResultCode.TRANSIT_CHART_NOT_FOUND);
            }
            return wrapper;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse transit chart from DB for interpret: userId={}", userId);
            throw new BusinessException(ResultCode.TRANSIT_CHART_NOT_FOUND);
        }
    }

    // ─────────────────────── RAG Query 构建 ────────────────────────────────

    private String buildNatalRagQuery(JsonNode chart, String focus) {
        StringBuilder query = new StringBuilder("本命盘占星解读");
        if (StringUtils.hasText(focus)) {
            query.append(" ").append(focus);
        }
        appendChartKeywords(query, chart);
        return query.toString();
    }

    private String buildSynastryRagQuery(JsonNode chart, String focus) {
        StringBuilder query = new StringBuilder("合盘相位分析 感情兼容性");
        if (StringUtils.hasText(focus)) {
            query.append(" ").append(focus);
        }
        appendChartKeywords(query, chart);
        return query.toString();
    }

    private String buildTransitRagQuery(JsonNode chart, String focus) {
        StringBuilder query = new StringBuilder("行星过境流运解读 近期星象");
        if (StringUtils.hasText(focus)) {
            query.append(" ").append(focus);
        }
        appendTransitKeywords(query, chart);
        return query.toString();
    }

    /**
     * 从流运数据（{date, events, summary}）中提取 RAG 检索关键词。
     *
     * <p>与 natal/synastry 的 chart 数据格式不同，transit_chart_data 格式为：
     * <pre>
     * {
     *   "date":    "2025-01-15",
     *   "events":  [ {transit_planet, natal_planet, aspect_type, strength, tags, ...}, ... ],
     *   "summary": { emotional_state, energy_level, life_focus, ... }
     * }
     * </pre>
     */
    private void appendTransitKeywords(StringBuilder query, JsonNode transitData) {
        if (transitData == null) return;
        try {
            // 1. 从 summary 中提取整体状态关键词
            if (transitData.has("summary") && !transitData.get("summary").isNull()) {
                JsonNode summary = transitData.get("summary");
                appendIfPresent(query, summary, "emotional_state",  "情绪状态");
                appendIfPresent(query, summary, "energy_level",     "能量水平");
                appendIfPresent(query, summary, "life_focus",       "焦点");
            }

            // 2. 从 events 中提取最重要的相位行星关键词（按 strength 降序取前 5）
            if (transitData.has("events") && transitData.get("events").isArray()) {
                java.util.List<JsonNode> eventList = new java.util.ArrayList<>();
                transitData.get("events").forEach(eventList::add);
                eventList.sort((a, b) -> {
                    double sa = a.has("strength") ? a.get("strength").asDouble() : 0;
                    double sb = b.has("strength") ? b.get("strength").asDouble() : 0;
                    return Double.compare(sb, sa);
                });
                int count = 0;
                for (JsonNode ev : eventList) {
                    if (count >= 5) break;
                    String tp = ev.has("transit_planet") ? ev.get("transit_planet").asText("") :
                            ev.has("planet") ? ev.get("planet").asText("") : "";
                    String np = ev.has("natal_planet") ? ev.get("natal_planet").asText("") :
                            ev.has("natal_body") ? ev.get("natal_body").asText("") : "";
                    String aspect = ev.has("aspect_type") ? ev.get("aspect_type").asText("") :
                            ev.has("aspect") ? ev.get("aspect").asText("") : "";
                    if (StringUtils.hasText(tp)) {
                        query.append(" ").append(tp);
                        if (StringUtils.hasText(aspect)) query.append("-").append(aspect);
                        if (StringUtils.hasText(np)) query.append("-").append(np);
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract transit keywords for RAG: {}", e.getMessage());
        }
    }

    /**
     * 将 chart 中除 metadata 外的所有属性提取为 RAG query 关键词。
     *
     * <p>数据结构（Python 返回）：
     * <pre>
     * chart: {
     *   planets: { sun:{sign,degree,house,retrograde}, moon:{...}, mercury, venus, mars,
     *              jupiter, saturn, uranus, neptune, pluto },
     *   angles:  { ascendant:{sign,...}, mc:{sign,...}, descendant:{sign,...}, ic:{sign,...} },
     *   nodes:   { north_node:{sign,...}, south_node:{sign,...} },
     *   houses:  { system:"Placidus", cusps:[...] },
     *   aspects: [ {p1,p2,aspect_type,orb,strength,...}, ... ],
     *   metadata: { ... }   ← 跳过
     * }
     * </pre>
     */
    private void appendChartKeywords(StringBuilder query, JsonNode chart) {
        if (chart == null) return;
        try {
            // ── 1. planets：十大行星，追加 星座 + 宫位 + 逆行标记 ─────────────
            if (chart.has("planets")) {
                JsonNode planets = chart.get("planets");
                appendPlanetFull(query, planets, "sun",     "太阳");
                appendPlanetFull(query, planets, "moon",    "月亮");
                appendPlanetFull(query, planets, "mercury", "水星");
                appendPlanetFull(query, planets, "venus",   "金星");
                appendPlanetFull(query, planets, "mars",    "火星");
                appendPlanetFull(query, planets, "jupiter", "木星");
                appendPlanetFull(query, planets, "saturn",  "土星");
                appendPlanetFull(query, planets, "uranus",  "天王星");
                appendPlanetFull(query, planets, "neptune", "海王星");
                appendPlanetFull(query, planets, "pluto",   "冥王星");
            }

            // ── 2. angles：四轴（上升/天顶/下降/天底），追加星座 ─────────────
            if (chart.has("angles")) {
                JsonNode angles = chart.get("angles");
                appendPlanetSign(query, angles, "ascendant",  "上升");
                appendPlanetSign(query, angles, "mc",         "天顶");
                appendPlanetSign(query, angles, "descendant", "下降");
                appendPlanetSign(query, angles, "ic",         "天底");
            }

            // ── 3. nodes：南北交点，追加星座 ─────────────────────────────────
            if (chart.has("nodes")) {
                JsonNode nodes = chart.get("nodes");
                appendPlanetSign(query, nodes, "north_node", "北交点");
                appendPlanetSign(query, nodes, "south_node", "南交点");
            }

            // ── 4. houses：宫位制名称 ─────────────────────────────────────────
            if (chart.has("houses")) {
                JsonNode houses = chart.get("houses");
                if (houses.has("system")) {
                    String system = houses.get("system").asText();
                    if (StringUtils.hasText(system) && !"null".equals(system)) {
                        query.append(" ").append(system).append("宫位制");
                    }
                }
            }

            // ── 5. aspects：取前 5 个最强相位（strength 最高），追加相位类型关键词 ──
            if (chart.has("aspects")) {
                JsonNode aspects = chart.get("aspects");
                if (aspects.isArray()) {
                    // 按 strength 降序取前 5
                    java.util.List<JsonNode> sorted = new java.util.ArrayList<>();
                    aspects.forEach(sorted::add);
                    sorted.sort((a, b) -> {
                        double sa = a.has("strength") ? a.get("strength").asDouble() : 0;
                        double sb = b.has("strength") ? b.get("strength").asDouble() : 0;
                        return Double.compare(sb, sa);
                    });
                    int count = 0;
                    for (JsonNode asp : sorted) {
                        if (count >= 5) break;
                        String p1 = asp.has("p1") ? asp.get("p1").asText("") : "";
                        String p2 = asp.has("p2") ? asp.get("p2").asText("") : "";
                        String type = asp.has("aspect_type") ? asp.get("aspect_type").asText("") : "";
                        if (StringUtils.hasText(p1) && StringUtils.hasText(p2) && StringUtils.hasText(type)) {
                            query.append(" ").append(p1).append("-").append(type).append("-").append(p2);
                            count++;
                        }
                    }
                }
            }

            // ── 6. 兼容 summary 节点（若 chart 中携带，同样提取） ─────────────
            if (chart.has("summary")) {
                JsonNode summary = chart.get("summary");
                appendPlanetSign(query, summary, "sun",       "太阳");
                appendPlanetSign(query, summary, "moon",      "月亮");
                appendPlanetSign(query, summary, "ascendant", "上升");
                appendIfPresent(query, summary, "sun_sign",        "太阳");
                appendIfPresent(query, summary, "moon_sign",       "月亮");
                appendIfPresent(query, summary, "rising_sign",     "上升");
                appendIfPresent(query, summary, "dominant_planet", "主星");
            }
        } catch (Exception e) {
            log.debug("Failed to extract chart keywords for RAG: {}", e.getMessage());
        }
    }

    /**
     * 提取单颗行星的完整关键词：星座 + 宫位（可选）+ 逆行标记（可选）。
     * 例：「太阳Virgo 第10宫」「水星Libra（逆行）」
     */
    private void appendPlanetFull(StringBuilder query, JsonNode parent, String planet, String label) {
        if (!parent.has(planet)) return;
        JsonNode node = parent.get(planet);
        if (!node.isObject()) return;

        // 星座
        String sign = node.has("sign") ? node.get("sign").asText("") : "";
        if (!StringUtils.hasText(sign) || "null".equals(sign)) return;
        query.append(" ").append(label).append(sign);

        // 宫位
        if (node.has("house")) {
            int house = node.get("house").asInt(0);
            if (house > 0) {
                query.append("第").append(house).append("宫");
            }
        }

        // 逆行
        if (node.has("retrograde") && node.get("retrograde").asBoolean(false)) {
            query.append("（逆行）");
        }
    }

    /**
     * 从行星/轴点节点中提取 sign 字段并追加到 query。
     * 兼容两种格式：
     * - 嵌套对象: { "sun": { "sign": "Virgo", ... } }
     * - 扁平字符串: { "sun": "Virgo" }
     */
    private void appendPlanetSign(StringBuilder query, JsonNode parent, String planet, String label) {
        if (!parent.has(planet)) return;
        JsonNode node = parent.get(planet);
        String sign = null;
        if (node.isObject() && node.has("sign")) {
            sign = node.get("sign").asText();
        } else if (node.isTextual()) {
            sign = node.asText();
        }
        if (StringUtils.hasText(sign) && !"null".equals(sign)) {
            query.append(" ").append(label).append(sign);
        }
    }

    private void appendIfPresent(StringBuilder sb, JsonNode node, String field, String label) {
        if (node.has(field)) {
            String value = node.get(field).asText();
            if (StringUtils.hasText(value) && !"null".equals(value)) {
                sb.append(" ").append(label).append(value);
            }
        }
    }

    // ─────────────────────── 用户层指令 ────────────────────────────────────

    private String buildUserInstruction(String type, String focus, String extraContext) {
        String focusDesc = StringUtils.hasText(focus) ? ("，重点关注「" + focus + "」维度") : "，请进行全面解读";
        return switch (type) {
            case "NATAL" -> "请为我解读这份本命盘" + focusDesc + "。";
            case "SYNASTRY" -> {
                String partner = extractPartnerName(extraContext);
                yield "请为我解读与" + partner + "的和盘关系" + focusDesc + "。";
            }
            case "TRANSIT" -> {
                String date = StringUtils.hasText(extraContext) ? ("（" + extraContext + "）") : "";
                yield "请为我解读近期流运星象" + date + focusDesc + "。";
            }
            default -> "请为我进行占星解读" + focusDesc + "。";
        };
    }

    private String extractPartnerName(String extraContext) {
        if (!StringUtils.hasText(extraContext)) return "对方";
        String[] parts = extraContext.split("\\|");
        return parts[0].trim().isEmpty() ? "对方" : parts[0].trim();
    }
}

