package com.mindecho.module.memory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mindecho.module.memory.entity.Memory;
import com.mindecho.module.memory.mapper.MemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 记忆遗忘服务（Memory Forget Service）
 *
 * <h3>功能</h3>
 * <ol>
 *   <li>每天中午12点计算所有用户的 memoryScore</li>
 *   <li>当用户记忆条数达到阈值时触发遗忘压缩</li>
 *   <li>对分数最低的 10% 记忆生成摘要，删除原始记忆，写入 SUMMARY</li>
 * </ol>
 *
 * <h3>memoryScore 计算公式</h3>
 * <pre>
 * memoryScore = 0.5 × importance_norm + 0.3 × frequency_norm + 0.2 × recency_norm
 *
 * importance_norm = importance / 10
 * frequency_norm  = log(recallCount + 1) / log(maxRecallCount + 1)  [Log Scaling]
 * recency_norm    = e^(-days / halfLife)  [指数衰减，halfLife=30天]
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryForgetService {

    /** 指数衰减半衰期（天），recency_norm = e^(-days/halfLife) */
    private static final double DEFAULT_HALF_LIFE_DAYS = 30.0;

    /** 遗忘时每次压缩的比例（最低 10% 的记忆） */
    private static final double FORGET_RATIO = 0.1;

    @Value("${mindecho.memory.max-memory-count:1000}")
    private int maxMemoryCount;

    @Value("${mindecho.memory.max-vector-count:1000}")
    private int maxVectorCount;

    @Value("${mindecho.memory.half-life-days:30}")
    private double halfLifeDays;

    private final MemoryMapper memoryMapper;
    private final MemoryService memoryService;
    private final MemoryVectorService memoryVectorService;
    private final MemoryCacheService memoryCacheService;
    private final JdbcTemplate jdbcTemplate;

    // ─── 定时任务 ─────────────────────────────────────────────────────────────

    /**
     * 每天中午12点执行记忆分数计算与遗忘压缩
     */
    @Scheduled(cron = "0 0 12 * * ?")
    public void dailyMemoryMaintenance() {
        log.info("MemoryForgetService: starting daily memory maintenance");
        try {
            // 获取所有有记忆数据的用户 ID 列表
            List<UUID> userIds = getActiveUserIds();
            log.info("MemoryForgetService: processing {} users", userIds.size());

            for (UUID userId : userIds) {
                try {
                    processUserMemory(userId);
                } catch (Exception e) {
                    log.error("MemoryForgetService: failed to process userId={}: {}", userId, e.getMessage());
                }
            }

            log.info("MemoryForgetService: daily maintenance completed");
        } catch (Exception e) {
            log.error("MemoryForgetService: daily maintenance failed", e);
        }
    }

    /**
     * 处理单个用户的记忆维护（计算分数 + 必要时触发遗忘压缩）
     */
    @Transactional
    public void processUserMemory(UUID userId) {
        // Step1: 更新 Memory 表的 memoryScore
        updateMemoryScores(userId);

        // Step2: 检查 Memory 表是否超出阈值
        long memoryCount = memoryMapper.countByUserId(userId);
        if (memoryCount >= maxMemoryCount) {
            log.info("MemoryForgetService: Memory table exceeds threshold({}) for userId={}, count={}",
                    maxMemoryCount, userId, memoryCount);
            triggerMemoryCompression(userId, (int) memoryCount);
        }

        // Step3: 更新 memory_vector 表的 memoryScore
        updateVectorMemoryScores(userId);

        // Step4: 检查 memory_vector 表是否超出阈值
        long vectorCount = memoryVectorService.countByUserId(userId.toString());
        if (vectorCount >= maxVectorCount) {
            log.info("MemoryForgetService: Vector table exceeds threshold({}) for userId={}, count={}",
                    maxVectorCount, userId, vectorCount);
            triggerVectorCompression(userId.toString(), (int) vectorCount);
        }
    }

    // ─── Memory 表分数计算 ────────────────────────────────────────────────────

    /**
     * 计算并更新 Memory 表中指定用户所有记忆的 memoryScore
     *
     * <p>公式：
     * <pre>
     * memoryScore = 0.5 × importance_norm
     *             + 0.3 × frequency_norm
     *             + 0.2 × recency_norm
     * </pre>
     */
    private void updateMemoryScores(UUID userId) {
        List<Memory> memories = memoryMapper.selectList(
                new LambdaQueryWrapper<Memory>()
                        .eq(Memory::getUserId, userId)
                        .eq(Memory::getDeleted, 0)
        );

        if (memories.isEmpty()) return;

        // 获取该用户最大召回次数（用于归一化）
        long maxRecallCount = memoryMapper.getMaxRecallCount(userId);

        OffsetDateTime now = OffsetDateTime.now();
        double effectiveHalfLife = halfLifeDays > 0 ? halfLifeDays : DEFAULT_HALF_LIFE_DAYS;

        for (Memory memory : memories) {
            double score = calculateScore(memory, maxRecallCount, now, effectiveHalfLife);
            memory.setMemoryScore(score);
            memoryMapper.updateById(memory);
        }

        log.debug("MemoryForgetService: updated memoryScore for {} records, userId={}", memories.size(), userId);
    }

    /**
     * 计算单条记忆的 memoryScore
     */
    private double calculateScore(Memory memory, long maxRecallCount,
                                   OffsetDateTime now, double halfLifeDays) {
        // importance_norm = importance / 10
        double importanceNorm = Math.min(
                (memory.getImportanceScore() != null ? memory.getImportanceScore() : 5) / 10.0,
                1.0
        );

        // frequency_norm = log(recallCount + 1) / log(maxRecallCount + 1)  [Log Scaling]
        long recallCount = memory.getRecallCount() != null ? memory.getRecallCount() : 0L;
        double frequencyNorm;
        if (maxRecallCount <= 0) {
            frequencyNorm = 0.0;
        } else {
            frequencyNorm = Math.log(recallCount + 1) / Math.log(maxRecallCount + 1);
        }

        // recency_norm = e^(-days / halfLife)，使用 lastRecalledTime 而非 createdTime
        OffsetDateTime lastUsedTime = memory.getLastRecalledTime();
        if (lastUsedTime == null) {
            // 从未被召回，使用创建时间
            lastUsedTime = memory.getCreatedTime() != null ? memory.getCreatedTime() : now;
        }
        long days = Duration.between(lastUsedTime, now).toDays();
        double recencyNorm = Math.exp(-days / halfLifeDays);

        double score = 0.5 * importanceNorm + 0.3 * frequencyNorm + 0.2 * recencyNorm;
        // 确保分数在 [0, 1] 范围内
        return Math.max(0.0, Math.min(1.0, score));
    }

    // ─── Memory 表遗忘压缩 ────────────────────────────────────────────────────

    /**
     * 触发 Memory 表遗忘压缩
     *
     * <p>对 memoryScore 最低的 10% 记忆生成摘要，删除原始，写入 SUMMARY。
     *
     * @param userId      用户 ID
     * @param totalCount  当前总记忆数
     */
    private void triggerMemoryCompression(UUID userId, int totalCount) {
        int forgetCount = Math.max(1, (int) Math.ceil(totalCount * FORGET_RATIO));

        // 查找分数最低的 N 条记忆
        List<Memory> candidates = memoryMapper.findLowestScoreMemories(userId, forgetCount);
        if (candidates.isEmpty()) {
            // 降级：无 score 记录时按创建时间最早的 N 条
            candidates = memoryMapper.selectList(
                    new LambdaQueryWrapper<Memory>()
                            .eq(Memory::getUserId, userId)
                            .eq(Memory::getDeleted, 0)
                            .orderByAsc(Memory::getCreatedTime)
                            .last("LIMIT " + forgetCount)
            );
        }

        if (candidates.isEmpty()) return;

        log.info("MemoryForgetService: compressing {} memories for userId={}", candidates.size(), userId);

        // 生成摘要
        List<String> contents = candidates.stream()
                .map(Memory::getContent)
                .toList();
        String summary = memoryService.generateSummary(contents);

        if (summary != null && !summary.isBlank()) {
            // 写入摘要记忆
            memoryService.saveMemory(userId, MemoryDecisionEngine.TYPE_SUMMARY, summary, 8);
            log.info("MemoryForgetService: summary saved for userId={}", userId);
        }

        // 删除原始低分记忆（逻辑删除）
        List<UUID> idsToDelete = candidates.stream().map(Memory::getId).toList();
        memoryMapper.update(null,
                new LambdaUpdateWrapper<Memory>()
                        .in(Memory::getId, idsToDelete)
                        .set(Memory::getDeleted, 1)
        );
        log.info("MemoryForgetService: deleted {} low-score memories for userId={}", idsToDelete.size(), userId);

        // 遗忘压缩后使 Redis 缓存失效，等下次请求重新加载最新数据
        memoryCacheService.evict(userId);
        log.debug("MemoryForgetService: evicted Redis cache for userId={} after compression", userId);
    }

    // ─── memory_vector 表分数计算 ─────────────────────────────────────────────

    /**
     * 计算并更新 memory_vector 表中指定用户所有记忆的 memoryScore
     */
    private void updateVectorMemoryScores(UUID userId) {
        String userIdStr = userId.toString();
        try {
            long maxRecallCount = memoryVectorService.getMaxVectorRecallCount(userIdStr);
            OffsetDateTime now = OffsetDateTime.now();
            double effectiveHalfLife = halfLifeDays > 0 ? halfLifeDays : DEFAULT_HALF_LIFE_DAYS;

            // 查询所有向量记忆的关键字段
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT id::text,
                           COALESCE((metadata->>'importance_score')::int, 5) AS importance,
                           COALESCE(recall_count, 0)                          AS recall_count,
                           last_recalled_time,
                           created_time
                    FROM memory_vector
                    WHERE (metadata->>'user_id') = ?
                    """,
                    userIdStr
            );

            for (Map<String, Object> row : rows) {
                String id = (String) row.get("id");
                int importance = ((Number) row.get("importance")).intValue();
                long recallCount = ((Number) row.get("recall_count")).longValue();

                java.sql.Timestamp lastRecalled = (java.sql.Timestamp) row.get("last_recalled_time");
                java.sql.Timestamp created = (java.sql.Timestamp) row.get("created_time");

                OffsetDateTime lastUsedTime = lastRecalled != null
                        ? lastRecalled.toInstant().atOffset(java.time.ZoneOffset.UTC)
                        : (created != null ? created.toInstant().atOffset(java.time.ZoneOffset.UTC) : now);

                // 计算各因子
                double importanceNorm = Math.min(importance / 10.0, 1.0);

                double frequencyNorm = maxRecallCount <= 0 ? 0.0
                        : Math.log(recallCount + 1) / Math.log(maxRecallCount + 1);

                long days = Duration.between(lastUsedTime, now).toDays();
                double recencyNorm = Math.exp(-days / effectiveHalfLife);

                double score = Math.max(0.0, Math.min(1.0,
                        0.5 * importanceNorm + 0.3 * frequencyNorm + 0.2 * recencyNorm));

                jdbcTemplate.update(
                        "UPDATE memory_vector SET memory_score = ? WHERE id::text = ?",
                        score, id
                );
            }

            log.debug("MemoryForgetService: updated vector memoryScore for {} records, userId={}",
                    rows.size(), userId);
        } catch (Exception e) {
            log.error("MemoryForgetService: updateVectorMemoryScores failed for userId={}: {}", userId, e.getMessage());
        }
    }

    // ─── memory_vector 表遗忘压缩 ─────────────────────────────────────────────

    /**
     * 触发 memory_vector 表遗忘压缩
     */
    private void triggerVectorCompression(String userId, int totalCount) {
        int forgetCount = Math.max(1, (int) Math.ceil(totalCount * FORGET_RATIO));

        List<String> candidateIds = memoryVectorService.findLowestScoreVectorIds(userId, forgetCount);
        if (candidateIds.isEmpty()) return;

        log.info("MemoryForgetService: compressing {} vector memories for userId={}", candidateIds.size(), userId);

        // 获取候选内容
        List<Map<String, Object>> details = memoryVectorService.findVectorMemoryDetails(candidateIds);
        List<String> contents = details.stream()
                .map(row -> (String) row.get("content"))
                .filter(c -> c != null && !c.isBlank())
                .toList();

        if (!contents.isEmpty()) {
            String summary = memoryService.generateSummary(contents);
            if (summary != null && !summary.isBlank()) {
                // 向量摘要写入 memory_vector 表
                String newVectorId = UUID.randomUUID().toString();
                memoryVectorService.upsertMemoryVector(
                        newVectorId, newVectorId, userId,
                        MemoryDecisionEngine.TYPE_SUMMARY, summary, 8
                );
                log.info("MemoryForgetService: vector summary saved for userId={}", userId);
            }
        }

        // 物理删除低分向量记忆
        memoryVectorService.physicalDeleteByIds(candidateIds);
        log.info("MemoryForgetService: deleted {} low-score vector memories for userId={}", candidateIds.size(), userId);
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────────

    /**
     * 获取有记忆数据的活跃用户 ID 列表
     */
    private List<UUID> getActiveUserIds() {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT DISTINCT user_id FROM memory WHERE deleted = 0",
                    UUID.class
            );
        } catch (Exception e) {
            log.error("MemoryForgetService: getActiveUserIds failed: {}", e.getMessage());
            return List.of();
        }
    }
}

