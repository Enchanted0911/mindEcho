package com.mindecho.module.astrology.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mindecho.module.astrology.client.AstrologyPythonClient;
import com.mindecho.module.astrology.dto.AstrologyInterpretRequestDTO;
import com.mindecho.module.astrology.dto.AstrologyInterpretResponseDTO;
import com.mindecho.module.astrology.memory.AstrologyMemoryService;
import com.mindecho.module.astrology.prompt.AstrologerPromptBuilder;
import com.mindecho.module.billing.entity.AiUsageRecord;
import com.mindecho.module.billing.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AI 占星解读服务（Java AI Emotional Astrology Brain）
 *
 * <p>核心功能：将 Python 计算的星盘数据转化为温柔、有洞察力的 AI 占星解读。
 *
 * <p>处理流程（按 PRD §8）：
 * <pre>
 * 星盘数据
 *   ↓
 * Python RAG 检索（调用 /internal/rag/query，带 6h 缓存）
 *   ↓
 * 用户 Memory 检索（按解读类型差异化策略）
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
 * 异步保存解读历史到 Memory（astrology_history 类型）
 * </pre>
 *
 * <p>RAG 缓存（PRD §7.2）：{@code rag:{queryHash}} TTL = 6h
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AstrologyAiService {

    private static final long TTL_RAG_HOURS = 6L;
    private static final int RAG_TOP_K = 5;

    /** 触发 RAG 检索的最小 chart JSON 字段数（chart 为 null 或极小时跳过 RAG） */
    private static final int MIN_CHART_SIZE = 10;

    private final AstrologyPythonClient pythonClient;
    private final AstrologyMemoryService astrologyMemoryService;
    private final AstrologerPromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BillingService billingService;

    @Value("${astrology.rag.enabled:true}")
    private boolean ragEnabled;

    // ─────────────────────── 单盘解读 ─────────────────────────────────────

    /**
     * 单星盘（本命盘）AI 解读
     *
     * @param userId  当前用户 ID
     * @param request 解读请求（含 chart 数据、focus、tone）
     * @return AI 占星解读结果
     */
    public AstrologyInterpretResponseDTO interpretNatal(String userId, AstrologyInterpretRequestDTO request) {
        log.info("Interpreting natal chart: userId={}, focus={}", userId, request.getFocus());

        // Step 1: RAG 检索
        String ragQuery = buildNatalRagQuery(request.getChart(), request.getFocus());
        String ragContent = queryRagWithCache(ragQuery);
        boolean ragFused = StringUtils.hasText(ragContent);

        // Step 2: Memory 检索（pgvector 语义检索，query 使用 RAG query 兜底）
        List<String> memories = astrologyMemoryService.recallForNatal(userId, ragQuery);
        boolean memoryFused = !memories.isEmpty();

        // Step 3: 构建 Prompt
        String systemPrompt = promptBuilder.buildNatalPrompt(
                request.getChart(), ragContent, memories,
                request.getFocus(), request.getTone());
        String userInstruction = buildUserInstruction("NATAL", request.getFocus(), request.getExtraContext());

        // Step 4: 预扣积分
        int estimatedTokens = estimatePromptTokens(systemPrompt, userInstruction);
        AiUsageRecord usageRecord = initBillingQuietly(userId, "ASTROLOGY_NATAL", estimatedTokens);

        // Step 5: 调用 LLM（含 token 统计）
        LlmResult result = callLlmWithUsage(systemPrompt, userInstruction);

        // Step 6: 积分结算
        settleBillingQuietly(usageRecord, userId, result);

        // Step 7: 异步保存解读历史到向量记忆（astrology_history 类型）
        saveHistoryAsync(userId, "NATAL", result.content, request.getFocus());
        // Step 8: 异步从解读结果中提取并保存情绪特征（emotion 类型）
        astrologyMemoryService.extractAndSaveInterpretationEmotionAsync(
                userId, "NATAL", result.content, request.getFocus());

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
     * 和盘 AI 解读
     *
     * @param userId  当前用户 ID
     * @param request 解读请求
     * @return AI 占星解读结果
     */
    public AstrologyInterpretResponseDTO interpretSynastry(String userId, AstrologyInterpretRequestDTO request) {
        log.info("Interpreting synastry chart: userId={}, focus={}", userId, request.getFocus());

        String ragQuery = buildSynastryRagQuery(request.getChart(), request.getFocus());
        String ragContent = queryRagWithCache(ragQuery);
        boolean ragFused = StringUtils.hasText(ragContent);

        List<String> memories = astrologyMemoryService.recallForSynastry(userId, ragQuery);
        boolean memoryFused = !memories.isEmpty();

        String partnerName = extractPartnerName(request.getExtraContext());
        String relationshipType = extractRelationshipType(request.getExtraContext());

        String systemPrompt = promptBuilder.buildSynastryPrompt(
                request.getChart(), ragContent, memories,
                partnerName, relationshipType,
                request.getFocus(), request.getTone());
        String userInstruction = buildUserInstruction("SYNASTRY", request.getFocus(), request.getExtraContext());

        int estimatedTokens = estimatePromptTokens(systemPrompt, userInstruction);
        AiUsageRecord usageRecord = initBillingQuietly(userId, "ASTROLOGY_SYNASTRY", estimatedTokens);

        LlmResult result = callLlmWithUsage(systemPrompt, userInstruction);

        settleBillingQuietly(usageRecord, userId, result);

        saveHistoryAsync(userId, "SYNASTRY", result.content, request.getFocus());
        // 和盘解读：额外保存关系记录到向量记忆（relationship 类型）
        // 从解读内容中提取关系主题并保存
        astrologyMemoryService.extractAndSaveRelationshipFromInterpretAsync(
                userId, partnerName, relationshipType, result.content);

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
     * 流运 AI 解读
     *
     * @param userId  当前用户 ID
     * @param request 解读请求
     * @return AI 占星解读结果
     */
    public AstrologyInterpretResponseDTO interpretTransit(String userId, AstrologyInterpretRequestDTO request) {
        log.info("Interpreting transit chart: userId={}, focus={}", userId, request.getFocus());

        String ragQuery = buildTransitRagQuery(request.getChart(), request.getFocus());
        String ragContent = queryRagWithCache(ragQuery);
        boolean ragFused = StringUtils.hasText(ragContent);

        List<String> memories = astrologyMemoryService.recallForTransit(userId, ragQuery);
        boolean memoryFused = !memories.isEmpty();

        String targetDate = request.getExtraContext();

        String systemPrompt = promptBuilder.buildTransitPrompt(
                request.getChart(), ragContent, memories,
                targetDate, request.getFocus(), request.getTone());
        String userInstruction = buildUserInstruction("TRANSIT", request.getFocus(), request.getExtraContext());

        int estimatedTokens = estimatePromptTokens(systemPrompt, userInstruction);
        AiUsageRecord usageRecord = initBillingQuietly(userId, "ASTROLOGY_TRANSIT", estimatedTokens);

        LlmResult result = callLlmWithUsage(systemPrompt, userInstruction);

        settleBillingQuietly(usageRecord, userId, result);

        saveHistoryAsync(userId, "TRANSIT", result.content, request.getFocus());
        // 流运解读：提取近期情绪倾向写入向量记忆（emotion 类型）
        astrologyMemoryService.extractAndSaveInterpretationEmotionAsync(
                userId, "TRANSIT", result.content, request.getFocus());

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
    private AiUsageRecord initBillingQuietly(String userId, String businessType, int estimatedTokens) {
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
    private void settleBillingQuietly(AiUsageRecord usageRecord, String userId, LlmResult result) {
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

        // 使用 SHA-256 前 8 字节（64 bit）作为 RAG 缓存 key，比 hashCode 碰撞概率低得多
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

    // ─────────────────────── 异步 Memory 写入 ─────────────────────────────

    private void saveHistoryAsync(String userId, String interpretType,
                                  String interpretation, String focus) {
        astrologyMemoryService.saveInterpretationHistoryAsync(userId, interpretType, interpretation, focus);
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
        appendChartKeywords(query, chart);
        return query.toString();
    }

    private void appendChartKeywords(StringBuilder query, JsonNode chart) {
        if (chart == null || chart.size() < MIN_CHART_SIZE) return;
        try {
            JsonNode summary = chart.has("summary") ? chart.get("summary") : null;
            if (summary != null) {
                appendIfPresent(query, summary, "sun_sign", "太阳");
                appendIfPresent(query, summary, "moon_sign", "月亮");
                appendIfPresent(query, summary, "rising_sign", "上升");
                appendIfPresent(query, summary, "dominant_planet", "主星");
            }
            if (chart.has("planets")) {
                JsonNode planets = chart.get("planets");
                if (planets.has("sun") && planets.get("sun").has("sign")) {
                    query.append(" 太阳").append(planets.get("sun").get("sign").asText());
                }
                if (planets.has("moon") && planets.get("moon").has("sign")) {
                    query.append(" 月亮").append(planets.get("moon").get("sign").asText());
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract chart keywords for RAG: {}", e.getMessage());
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

    private String extractRelationshipType(String extraContext) {
        if (!StringUtils.hasText(extraContext)) return "romantic";
        String[] parts = extraContext.split("\\|");
        return parts.length > 1 ? parts[1].trim() : "romantic";
    }
}

