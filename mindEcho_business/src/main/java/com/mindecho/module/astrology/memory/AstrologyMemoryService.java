package com.mindecho.module.astrology.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.mindecho.module.memory.entity.Memory;
import com.mindecho.module.memory.mapper.MemoryMapper;
import com.mindecho.module.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 占星专属 Memory 服务
 *
 * <p>在通用 {@link MemoryService} 基础上，管理占星场景特有的记忆类型：
 * <ul>
 *   <li>{@code emotion}        — 情绪标签（焦虑、孤独、兴奋…）</li>
 *   <li>{@code relationship}   — 关系记录（分手、依恋、和解…）</li>
 *   <li>{@code profile}        — 人格画像（敏感、回避型依恋…）</li>
 *   <li>{@code astrology_history} — 历史星盘解读摘要</li>
 * </ul>
 *
 * <p>Memory 检索策略：
 * <ul>
 *   <li>单盘解读（NATAL）→ 返回 profile + emotion + summary</li>
 *   <li>和盘解读（SYNASTRY）→ 返回 relationship + emotion + profile</li>
 *   <li>流运解读（TRANSIT）→ 返回 emotion + summary + profile</li>
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

    private final MemoryMapper memoryMapper;
    private final MemoryService memoryService;

    // ─────────────────────── Memory 检索 ─────────────────────────────────

    /**
     * 为单盘解读检索相关 Memory
     * 策略：profile（人格画像）+ emotion（情绪历史）+ summary（对话摘要）+ astrology_history
     */
    public List<Memory> recallForNatal(Long userId) {
        return recallByTypes(userId, List.of(TYPE_PROFILE, TYPE_EMOTION, TYPE_SUMMARY, TYPE_ASTROLOGY_HISTORY));
    }

    /**
     * 为和盘解读检索相关 Memory
     * 策略：relationship（关系记录）+ emotion（情绪历史）+ profile（人格）
     * 重点：用户的恋爱历史、情感依赖模式、长期关系偏好
     */
    public List<Memory> recallForSynastry(Long userId) {
        return recallByTypes(userId, List.of(TYPE_RELATIONSHIP, TYPE_EMOTION, TYPE_PROFILE));
    }

    /**
     * 为流运解读检索相关 Memory
     * 策略：emotion（当前情绪状态）+ summary（历史摘要）+ profile（人格）
     * 重点：当前情绪、压力来源、感情变化信号
     */
    public List<Memory> recallForTransit(Long userId) {
        return recallByTypes(userId, List.of(TYPE_EMOTION, TYPE_SUMMARY, TYPE_PROFILE));
    }

    // ─────────────────────── Memory 写入 ─────────────────────────────────

    /**
     * 异步保存本次 AI 解读摘要到 astrology_history Memory
     * （用于下次解读时的历史参考）
     *
     * @param userId          用户 ID
     * @param interpretType   解读类型（NATAL/SYNASTRY/TRANSIT）
     * @param interpretation  AI 生成的解读文本（截取前 300 字存储）
     * @param focus           解读焦点
     */
    @Async
    public void saveInterpretationHistoryAsync(Long userId, String interpretType,
                                               String interpretation, String focus) {
        try {
            if (!StringUtils.hasText(interpretation)) return;

            // 截取前 300 字作为摘要
            String summary = interpretation.length() > 300
                    ? interpretation.substring(0, 300) + "…"
                    : interpretation;

            String prefix = buildHistoryPrefix(interpretType, focus);
            String content = prefix + summary;

            // 软删除旧的同类型历史（每类型保留最新 1 条，防止无限增长）
            List<Memory> existing = memoryMapper.selectList(
                    new LambdaQueryWrapper<Memory>()
                            .eq(Memory::getUserId, userId)
                            .eq(Memory::getMemoryType, TYPE_ASTROLOGY_HISTORY)
                            .eq(Memory::getDeleted, 0)
            );
            existing.stream()
                    .filter(m -> m.getContent().startsWith(prefix))
                    .forEach(m -> {
                        m.setDeleted(1);
                        memoryMapper.updateById(m);
                    });

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
    public void extractAndSaveChartEmotionTagsAsync(Long userId, JsonNode chartJson, String chartType) {
        try {
            if (chartJson == null) return;

            // 从 Python 服务的 summary 字段中提取情绪相关关键词
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
     *
     * @param userId           用户 ID
     * @param partnerName      对方名字
     * @param relationshipType 关系类型
     * @param themes           和盘主题标签列表
     */
    @Async
    public void saveRelationshipRecord(Long userId, String partnerName,
                                       String relationshipType, List<String> themes) {
        try {
            if (themes == null || themes.isEmpty()) return;
            String themeStr = String.join("、", themes);
            String content = String.format("[%s] 与%s的关系分析：%s",
                    mapRelationshipType(relationshipType), partnerName, themeStr);
            memoryService.saveMemory(userId, TYPE_RELATIONSHIP, content, 8);
            log.debug("Relationship memory saved: userId={}, partner={}", userId, partnerName);
        } catch (Exception e) {
            log.warn("Failed to save relationship memory: userId={}", userId, e);
        }
    }

    // ─────────────────────── 私有方法 ─────────────────────────────────────

    /**
     * 按指定类型顺序检索 Memory，合并去重
     */
    private List<Memory> recallByTypes(Long userId, List<String> types) {
        List<Memory> result = new ArrayList<>();
        for (String type : types) {
            List<Memory> memories = memoryMapper.selectList(
                    new LambdaQueryWrapper<Memory>()
                            .eq(Memory::getUserId, userId)
                            .eq(Memory::getMemoryType, type)
                            .eq(Memory::getDeleted, 0)
                            .orderByDesc(Memory::getImportanceScore)
            );
            // 每种类型最多取前 3 条（按重要度降序）
            result.addAll(memories.stream().limit(3).collect(Collectors.toList()));
        }
        log.debug("Recalled {} memories for userId={}", result.size(), userId);
        return result;
    }

    private void extractEmotionFromSummary(Long userId, JsonNode summary, String chartType) {
        if (summary == null) return;
        // 从 summary 中提取情绪相关字段（根据 Python 服务的实际返回结构调整）
        String[] emotionFields = {"dominant_emotion", "emotional_pattern", "mood"};
        for (String field : emotionFields) {
            if (summary.has(field)) {
                String value = summary.get(field).asText();
                if (StringUtils.hasText(value) && !"null".equals(value)) {
                    String content = String.format("[%s] 情绪特征：%s", chartType, value);
                    memoryService.saveMemory(userId, TYPE_EMOTION, content, 6);
                }
            }
        }
    }

    private void extractEmotionFromTransitEvents(Long userId, JsonNode events) {
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

