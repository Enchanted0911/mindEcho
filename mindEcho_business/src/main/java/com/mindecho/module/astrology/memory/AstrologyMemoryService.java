package com.mindecho.module.astrology.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.UUID;

/**
 * 占星专属 Memory 服务
 *
 * <p>管理占星场景特有的用户关系记忆类型：
 * <ul>
 *   <li>{@code relationship} — 关系记录（分手、依恋、和解…），写入通用 memory 供主聊天感知</li>
 * </ul>
 *
 * <p>注意：星盘解读历史（astrology_history）和星盘提取的情绪标签（emotion from chart）
 * 不再写入 memory 表。星盘数据直接从 user_astrology 表读取，由主聊天 Prompt 按需加载。
 *
 * <p>Memory 检索策略（基于 pgvector 语义相似度检索）：
 * <ul>
 *   <li>单盘解读（NATAL）    → profile + emotion + summary</li>
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
    private static final String TYPE_SUMMARY = "summary";

    /** 占星场景语义检索时，每种类型最多返回条数 */
    private static final int TOP_K_PER_TYPE = 3;

    private final MemoryMapper memoryMapper;
    private final MemoryService memoryService;
    private final MemoryVectorService memoryVectorService;

    // ─────────────────────── Memory 检索 ─────────────────────────────────

    /**
     * 为单盘解读检索相关 Memory（语义检索）
     * 策略：profile + emotion + summary
     *
     * @param userId 用户 ID
     * @param query  当前解读焦点或用户提问（作为语义 query）
     */
    public List<String> recallForNatal(UUID userId, String query) {
        return recallByTypesVectorSearch(userId,
                List.of(TYPE_PROFILE, TYPE_EMOTION, TYPE_SUMMARY),
                query);
    }

    /**
     * 为和盘解读检索相关 Memory（语义检索）
     * 策略：relationship + emotion + profile
     *
     * @param userId 用户 ID
     * @param query  当前解读焦点或用户提问
     */
    public List<String> recallForSynastry(UUID userId, String query) {
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
    public List<String> recallForTransit(UUID userId, String query) {
        return recallByTypesVectorSearch(userId,
                List.of(TYPE_EMOTION, TYPE_SUMMARY, TYPE_PROFILE),
                query);
    }

    // ─────────────────────── Memory 写入 ─────────────────────────────────

    /**
     * 异步保存关系记录（和盘完成后调用）
     *
     * <p>关系记忆写入通用 memory 表，使主聊天也能感知用户的亲密关系状态。
     * （保存后自动向量化写入 pgvector）
     *
     * @param userId           用户 ID
     * @param partnerName      对方名字
     * @param relationshipType 关系类型
     * @param themes           和盘主题标签列表
     */
    @Async
    public void saveRelationshipRecord(UUID userId, String partnerName,
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
     * 异步从和盘解读文本中提取关系主题并写入向量记忆（relationship 类型）
     *
     * <p>将 AI 解读中的关系叙述写入向量，增强关系记忆的语义覆盖。
     * 主聊天可通过关系记忆感知用户的感情状态，提供更有温度的陪伴。
     *
     * @param userId           用户 ID
     * @param partnerName      对方名字
     * @param relationshipType 关系类型
     * @param interpretation   AI 和盘解读全文
     */
    @Async
    public void extractAndSaveRelationshipFromInterpretAsync(UUID userId, String partnerName,
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
    private List<String> recallByTypesVectorSearch(UUID userId, List<String> types, String query) {
        if (!StringUtils.hasText(query)) {
            // 无 query 时降级为按重要度排序
            return recallByTypesImportance(userId, types);
        }
        List<Document> docs = memoryVectorService.searchCandidatesByTypes(
                userId.toString(), types, query, TOP_K_PER_TYPE);
        List<String> contents = MemoryVectorService.extractContents(docs);
        log.debug("Astrology vector recall: userId={}, types={}, recalled={}",
                userId, types, contents.size());
        return contents;
    }

    /**
     * 降级方法：按重要度排序返回记忆内容
     */
    private List<String> recallByTypesImportance(UUID userId, List<String> types) {
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

    private String mapRelationshipType(String type) {
        return switch (type == null ? "romantic" : type) {
            case "family" -> "家庭";
            case "friendship" -> "友情";
            case "colleague" -> "职场";
            default -> "感情";
        };
    }
}

