package com.mindecho.module.astrology.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.mindecho.module.memory.entity.Memory;
import com.mindecho.module.memory.mapper.MemoryMapper;
import com.mindecho.module.memory.service.MemoryService;
import com.mindecho.module.memory.service.MemoryVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 占星专属 Memory 服务
 *
 * <p>在通用 {@link MemoryService} 基础上，管理占星场景特有的记忆类型：
 * <ul>
 *   <li>{@code emotion}           — 情绪标签（焦虑、孤独、兴奋…）</li>
 *   <li>{@code relationship}      — 关系记录（分手、依恋、和解…）</li>
 *   <li>{@code profile}           — 人格画像（敏感、回避型依恋…）</li>
 *   <li>{@code astrology_history} — 历史星盘解读摘要</li>
 * </ul>
 *
 * <p>Memory 检索策略（基于 pgvector 语义相似度检索）：
 * <ul>
 *   <li>单盘解读（NATAL）    → profile + emotion + summary + astrology_history</li>
 *   <li>和盘解读（SYNASTRY） → relationship + emotion + profile</li>
 *   <li>流运解读（TRANSIT）  → emotion + summary + profile</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AstrologyMemoryService {

    private static final String TYPE_EMOTION = "emotion";
    private static final String TYPE_RELATIONSHIP = "relationship";
    private static final String TYPE_PROFILE = "profile";
    private static final String TYPE_ASTROLOGY_HISTORY = "astrology_history";
    private static final String TYPE_SUMMARY = "summary";

    /** 占星场景语义检索时，每种类型最多返回条数 */
    private static final int TOP_K_PER_TYPE = 3;

    private final MemoryMapper memoryMapper;
    private final MemoryService memoryService;
    private final MemoryVectorService memoryVectorService;

    // ─────────────────────── Memory 检索 ─────────────────────────────────

    /**
     * 为单盘解读检索相关 Memory（语义检索）
     * 策略：profile + emotion + summary + astrology_history
     *
     * @param userId 用户 ID
     * @param query  当前解读焦点或用户提问（作为语义 query）
     */
    public List<String> recallForNatal(String userId, String query) {
        return recallByTypesVectorSearch(userId,
                List.of(TYPE_PROFILE, TYPE_EMOTION, TYPE_SUMMARY, TYPE_ASTROLOGY_HISTORY),
                query);
    }

    /**
     * 为和盘解读检索相关 Memory（语义检索）
     * 策略：relationship + emotion + profile
     *
     * @param userId 用户 ID
     * @param query  当前解读焦点或用户提问
     */
    public List<String> recallForSynastry(String userId, String query) {
        return recallByTypesVectorSearch(userId,
                List.of(TYPE_RELATIONSHIP, TYPE_EMOTION, TYPE_PROFILE),
                query);
    }

    /**
     * 为流运解读检索相关 Memory（语义检索）
     * 策略：emotion + summary + profile
     *
     * @param userId 用户 ID
     * @param query  当前解读焦点或用户提问
     */
    public List<String> recallForTransit(String userId, String query) {
        return recallByTypesVectorSearch(userId,
                List.of(TYPE_EMOTION, TYPE_SUMMARY, TYPE_PROFILE),
                query);
    }

    // ─────────────────────── Memory 写入 ─────────────────────────────────

    /**
     * 异步保存本次 AI 解读摘要到 astrology_history Memory
     * （保存后自动向量化写入 pgvector，用于下次解读时的历史参考）
     *
     * @param userId          用户 ID
     * @param interpretType   解读类型（NATAL/SYNASTRY/TRANSIT）
     * @param interpretation  AI 生成的解读文本（截取前 300 字存储）
     * @param focus           解读焦点
     */
    @Async
    public void saveInterpretationHistoryAsync(String userId, String interpretType,
                                               String interpretation, String focus) {
        try {
            if (!StringUtils.hasText(interpretation)) return;

            // 截取前 300 字作为摘要
            String summary = interpretation.length() > 300
                    ? interpretation.substring(0, 300) + "…"
                    : interpretation;

            String prefix = buildHistoryPrefix(interpretType, focus);
            String content = prefix + summary;

            // 软删除旧的同类型历史（每类型保留最新 1 条），同步删除 pgvector 向量
            List<Memory> existing = memoryMapper.selectList(
                    new LambdaQueryWrapper<Memory>()
                            .eq(Memory::getUserId, userId)
                            .eq(Memory::getMemoryType, TYPE_ASTROLOGY_HISTORY)
                            .eq(Memory::getDeleted, 0)
            );
            existing.stream()
                    .filter(m -> m.getContent().startsWith(prefix))
                    .forEach(memoryService::deleteMemory);

            // saveMemory 会自动写入 pgvector（astrology_history 属于 VECTORIZED_TYPES）
            memoryService.saveMemory(userId, TYPE_ASTROLOGY_HISTORY, content, 7);
            log.debug("Astrology history saved: userId={}, type={}", userId, interpretType);
        } catch (Exception e) {
            log.warn("Failed to save astrology history memory: userId={}", userId, e);
        }
    }

    /**
     * 异步从星盘解读中提取情绪标签并保存
     *
     * <p>从 chart 的 summary 字段或 events 中提取情绪相关标签（如「土星压迫感」「金星高峰期」）
     *
     * @param userId    用户 ID
     * @param chartJson 星盘数据
     * @param chartType NATAL / SYNASTRY / TRANSIT
     */
    @Async
    public void extractAndSaveChartEmotionTagsAsync(String userId, JsonNode chartJson, String chartType) {
        try {
            if (chartJson == null) return;

            if (chartJson.has("summary")) {
                JsonNode summary = chartJson.get("summary");
                extractEmotionFromSummary(userId, summary, chartType);
            }

            // 流运特殊处理：提取高能量事件标签
            if ("TRANSIT".equals(chartType) && chartJson.has("events")) {
                extractEmotionFromTransitEvents(userId, chartJson.get("events"));
            }
        } catch (Exception e) {
            log.warn("Failed to extract chart emotion tags: userId={}", userId, e);
        }
    }

    /**
     * 异步保存关系记录（和盘完成后调用）
     * （保存后自动向量化写入 pgvector）
     *
     * @param userId           用户 ID
     * @param partnerName      对方名字
     * @param relationshipType 关系类型
     * @param themes           和盘主题标签列表
     */
    @Async
    public void saveRelationshipRecord(String userId, String partnerName,
                                       String relationshipType, List<String> themes) {
        try {
            if (themes == null || themes.isEmpty()) return;
            String themeStr = String.join("、", themes);
            String content = String.format("[%s] 与%s的关系分析：%s",
                    mapRelationshipType(relationshipType), partnerName, themeStr);
            // relationship 属于 VECTORIZED_TYPES，会自动向量化
            memoryService.saveMemory(userId, TYPE_RELATIONSHIP, content, 8);
            log.debug("Relationship memory saved: userId={}, partner={}", userId, partnerName);
        } catch (Exception e) {
            log.warn("Failed to save relationship memory: userId={}", userId, e);
        }
    }

    /**
     * 异步从 AI 解读文本中提取情绪特征并写入向量记忆（emotion 类型）
     *
     * <p>从解读文本中取前 200 字作为情绪语义锚点，标注解读类型和焦点，
     * 便于后续对话通过 pgvector 检索相关情绪状态。
     *
     * @param userId         用户 ID
     * @param interpretType  解读类型（NATAL/TRANSIT）
     * @param interpretation AI 解读全文
     * @param focus          解读焦点（如「感情」「事业」）
     */
    @Async
    public void extractAndSaveInterpretationEmotionAsync(String userId, String interpretType,
                                                          String interpretation, String focus) {
        try {
            if (!StringUtils.hasText(interpretation)) return;
            // 取解读摘要片段作为情绪语义内容（前 200 字）
            String snippet = interpretation.length() > 200
                    ? interpretation.substring(0, 200) + "…"
                    : interpretation;
            String focusPart = StringUtils.hasText(focus) ? ("/" + focus) : "";
            String content = String.format("[%s%s 解读情绪] %s", interpretType, focusPart, snippet);
            memoryService.saveMemory(userId, TYPE_EMOTION, content, 6);
            log.debug("Interpretation emotion memory saved: userId={}, type={}", userId, interpretType);
        } catch (Exception e) {
            log.warn("Failed to save interpretation emotion memory: userId={}", userId, e);
        }
    }

    /**
     * 异步从和盘解读文本中提取关系主题并写入向量记忆（relationship 类型）
     *
     * <p>补充 {@link #saveRelationshipRecord} 的数据来源，将 AI 解读中的关系叙述
     * 也写入向量，增强关系记忆的语义覆盖。
     *
     * @param userId           用户 ID
     * @param partnerName      对方名字
     * @param relationshipType 关系类型
     * @param interpretation   AI 和盘解读全文
     */
    @Async
    public void extractAndSaveRelationshipFromInterpretAsync(String userId, String partnerName,
                                                              String relationshipType,
                                                              String interpretation) {
        try {
            if (!StringUtils.hasText(interpretation)) return;
            String partner = StringUtils.hasText(partnerName) ? partnerName : "对方";
            String relType = mapRelationshipType(relationshipType);
            String snippet = interpretation.length() > 200
                    ? interpretation.substring(0, 200) + "…"
                    : interpretation;
            String content = String.format("[%s关系解读] 与%s：%s", relType, partner, snippet);
            memoryService.saveMemory(userId, TYPE_RELATIONSHIP, content, 7);
            log.debug("Relationship interpret memory saved: userId={}, partner={}", userId, partner);
        } catch (Exception e) {
            log.warn("Failed to save relationship interpret memory: userId={}", userId, e);
        }
    }

    // ─────────────────────── 私有方法 ─────────────────────────────────────

    /**
     * 使用 pgvector 语义检索，按指定类型顺序检索 Memory 内容
     */
    private List<String> recallByTypesVectorSearch(String userId, List<String> types, String query) {
        if (!StringUtils.hasText(query)) {
            // 无 query 时降级为按重要度排序
            return recallByTypesImportance(userId, types);
        }
        List<Document> docs = memoryVectorService.searchMemoriesByTypes(
                userId, types, query, TOP_K_PER_TYPE);
        List<String> contents = MemoryVectorService.extractContents(docs);
        log.debug("Astrology vector recall: userId={}, types={}, recalled={}",
                userId, types, contents.size());
        return contents;
    }

    /**
     * 降级方法：按重要度排序返回记忆内容
     */
    private List<String> recallByTypesImportance(String userId, List<String> types) {
        List<String> result = new ArrayList<>();
        for (String type : types) {
            memoryMapper.selectList(
                            new LambdaQueryWrapper<Memory>()
                                    .eq(Memory::getUserId, userId)
                                    .eq(Memory::getMemoryType, type)
                                    .eq(Memory::getDeleted, 0)
                                    .orderByDesc(Memory::getImportanceScore)
                                    .last("LIMIT 3")
                    ).stream()
                    .map(Memory::getContent)
                    .forEach(result::add);
        }
        return result;
    }

    private void extractEmotionFromSummary(String userId, JsonNode summary, String chartType) {
        if (summary == null) return;
        String[] emotionFields = {"dominant_emotion", "emotional_pattern", "mood"};
        for (String field : emotionFields) {
            if (summary.has(field)) {
                String value = summary.get(field).asText();
                if (StringUtils.hasText(value) && !"null".equals(value)) {
                    String content = String.format("[%s] 情绪特征：%s", chartType, value);
                    // emotion 属于 VECTORIZED_TYPES，会自动向量化
                    memoryService.saveMemory(userId, TYPE_EMOTION, content, 6);
                }
            }
        }
    }

    private void extractEmotionFromTransitEvents(String userId, JsonNode events) {
        if (!events.isArray()) return;
        List<String> highImpactEvents = new ArrayList<>();
        events.forEach(event -> {
            if (event.has("impact") && "high".equals(event.get("impact").asText())) {
                String desc = event.has("description") ? event.get("description").asText() : "";
                if (StringUtils.hasText(desc)) {
                    highImpactEvents.add(desc);
                }
            }
        });
        if (!highImpactEvents.isEmpty()) {
            String content = "[TRANSIT] 近期重要流运：" + String.join("；", highImpactEvents);
            memoryService.saveMemory(userId, TYPE_EMOTION, content, 7);
        }
    }

    private String buildHistoryPrefix(String interpretType, String focus) {
        String focusPart = StringUtils.hasText(focus) ? ("/" + focus) : "";
        return "[" + interpretType + focusPart + "] ";
    }

    private String mapRelationshipType(String type) {
        return switch (type == null ? "romantic" : type) {
            case "family" -> "家庭";
            case "friendship" -> "友情";
            case "colleague" -> "职场";
            default -> "感情";
        };
    }
}

