package com.mindecho.module.memory.service;

import com.mindecho.module.memory.entity.Memory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Memory 表结构化记忆的 Redis 缓存服务
 *
 * <p><b>缓存策略</b>：
 * <ul>
 *   <li>以 {@code memory:user:{userId}} 为 Key，缓存该用户所有非逻辑删除的结构化记忆列表</li>
 *   <li>命中缓存直接返回，避免 PgSQL 查询</li>
 *   <li>写入/删除 DB 后立即调用 {@link #evict(UUID)} 使缓存失效</li>
 *   <li>遗忘压缩完成后同样调用 {@link #evict(UUID)}，等下次请求触发 DB 重载</li>
 *   <li>TTL 默认 24h，防止 Redis 内存无限堆积</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryCacheService {

    private static final String KEY_PREFIX = "memory:user:";

    @Value("${mindecho.memory.cache-ttl-hours:24}")
    private long cacheTtlHours;

    private final RedissonClient redissonClient;

    // ─── Key 构造 ────────────────────────────────────────────────────────────

    private String cacheKey(UUID userId) {
        return KEY_PREFIX + userId.toString();
    }

    // ─── 读取 ─────────────────────────────────────────────────────────────────

    /**
     * 从 Redis 读取用户的结构化记忆列表。
     *
     * @param userId 用户 ID
     * @return 缓存的记忆列表；若不存在（cache miss）则返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public List<Memory> getFromCache(UUID userId) {
        try {
            RBucket<Object> bucket = redissonClient.getBucket(cacheKey(userId));
            Object value = bucket.get();
            if (value instanceof List<?> list) {
                log.debug("MemoryCacheService: cache hit userId={}, size={}", userId, list.size());
                return (List<Memory>) list;
            }
            log.debug("MemoryCacheService: cache miss userId={}", userId);
            return null;
        } catch (Exception e) {
            log.warn("MemoryCacheService: getFromCache failed userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    // ─── 写入 ─────────────────────────────────────────────────────────────────

    /**
     * 将用户的结构化记忆列表写入 Redis，带 TTL。
     *
     * @param userId   用户 ID
     * @param memories 从 DB 加载的全量结构化记忆列表
     */
    public void putToCache(UUID userId, List<Memory> memories) {
        try {
            RBucket<Object> bucket = redissonClient.getBucket(cacheKey(userId));
            bucket.set(memories, cacheTtlHours, TimeUnit.HOURS);
            log.debug("MemoryCacheService: cached {} memories for userId={}, ttl={}h",
                    memories.size(), userId, cacheTtlHours);
        } catch (Exception e) {
            log.warn("MemoryCacheService: putToCache failed userId={}: {}", userId, e.getMessage());
        }
    }

    // ─── 失效 ─────────────────────────────────────────────────────────────────

    /**
     * 使指定用户的记忆缓存失效（写入/删除/遗忘压缩后调用）。
     *
     * @param userId 用户 ID
     */
    public void evict(UUID userId) {
        try {
            redissonClient.getBucket(cacheKey(userId)).delete();
            log.debug("MemoryCacheService: evicted cache for userId={}", userId);
        } catch (Exception e) {
            log.warn("MemoryCacheService: evict failed userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * 判断指定用户的缓存是否存在。
     */
    public boolean exists(UUID userId) {
        try {
            return redissonClient.getBucket(cacheKey(userId)).isExists();
        } catch (Exception e) {
            log.warn("MemoryCacheService: exists check failed userId={}: {}", userId, e.getMessage());
            return false;
        }
    }
}

