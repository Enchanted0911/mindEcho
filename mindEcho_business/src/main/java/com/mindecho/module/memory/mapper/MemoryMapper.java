package com.mindecho.module.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mindecho.module.memory.entity.Memory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

/**
 * 记忆 Mapper
 */
@Mapper
public interface MemoryMapper extends BaseMapper<Memory> {

    /**
     * BM25 全文检索（PostgreSQL ts_rank + to_tsvector）
     *
     * <p>按 BM25 相似度排序，检索 Memory 表中与 query 最相关的记忆。
     * 支持中英文分词（使用 simple 配置，适合短文本记忆场景）。
     *
     * @param userId  用户 ID
     * @param query   检索关键词（空格分隔）
     * @param limit   返回条数上限
     * @return 按相关度排序的记忆列表
     */
    @Select("""
            SELECT *
            FROM memory
            WHERE user_id = #{userId}
              AND deleted = 0
              AND to_tsvector('simple', content) @@ plainto_tsquery('simple', #{query})
            ORDER BY ts_rank(to_tsvector('simple', content), plainto_tsquery('simple', #{query})) DESC
            LIMIT #{limit}
            """)
    List<Memory> searchByBm25(@Param("userId") UUID userId,
                               @Param("query") String query,
                               @Param("limit") int limit);

    /**
     * 查询指定用户的最大召回次数（用于 frequency_norm 归一化）
     *
     * @param userId 用户 ID
     * @return 最大召回次数（若无记录返回 0）
     */
    @Select("""
            SELECT COALESCE(MAX(recall_count), 0)
            FROM memory
            WHERE user_id = #{userId}
              AND deleted = 0
            """)
    long getMaxRecallCount(@Param("userId") UUID userId);

    /**
     * 批量更新召回次数和最近召回时间
     *
     * @param ids 记忆 ID 列表
     */
    @Update("""
            UPDATE memory
            SET recall_count       = COALESCE(recall_count, 0) + 1,
                last_recalled_time = NOW(),
                updated_time       = NOW()
            WHERE id = ANY(CAST(#{ids} AS uuid[]))
              AND deleted = 0
            """)
    int batchUpdateRecall(@Param("ids") String ids);

    /**
     * 查询指定用户的记忆总条数（不含逻辑删除）
     *
     * @param userId 用户 ID
     * @return 记忆条数
     */
    @Select("""
            SELECT COUNT(*)
            FROM memory
            WHERE user_id = #{userId}
              AND deleted = 0
            """)
    long countByUserId(@Param("userId") UUID userId);

    /**
     * 查询 memoryScore 最低的 N 条记忆（用于遗忘压缩候选）
     *
     * @param userId 用户 ID
     * @param limit  条数
     * @return 按 memoryScore 升序排列的记忆列表
     */
    @Select("""
            SELECT *
            FROM memory
            WHERE user_id = #{userId}
              AND deleted = 0
              AND memory_score IS NOT NULL
            ORDER BY memory_score ASC
            LIMIT #{limit}
            """)
    List<Memory> findLowestScoreMemories(@Param("userId") UUID userId,
                                          @Param("limit") int limit);
}

