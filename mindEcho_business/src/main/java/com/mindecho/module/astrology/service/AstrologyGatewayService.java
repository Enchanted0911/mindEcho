package com.mindecho.module.astrology.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindecho.module.astrology.client.AstrologyPythonClient;
import com.mindecho.module.astrology.dto.*;
import com.mindecho.module.astrology.memory.AstrologyMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * 占星 Gateway 服务（Java → Python HTTP 路由层）
 *
 * <p>职责：
 * <ol>
 *   <li>接收前端出生/查询信息</li>
 *   <li>命中 Redis 缓存则直接返回，否则转发 Python 服务</li>
 *   <li>返回结构化星盘数据（不含 AI 解读）</li>
 *   <li>可选保存用户星盘档案（natal 结果持久化 Redis，永不过期）</li>
 * </ol>
 *
 * <p>Redis 缓存策略（PRD §7）：
 * <ul>
 *   <li>{@code natal:{userId}}  — 永久（本命盘不变）</li>
 *   <li>{@code synastry:{pairHash}} — 30 天</li>
 *   <li>{@code transit:{userId}:{date}} — 24 小时</li>
 * </ul>
 */
@Slf4j
@Service
public class AstrologyGatewayService {

    // ─────────────────────── Redis Key 前缀 & TTL ───────────────────────────

    private static final String KEY_NATAL_PREFIX = "natal:";
    private static final String KEY_SYNASTRY_PREFIX = "synastry:";
    private static final String KEY_TRANSIT_PREFIX = "transit:";

    private static final long TTL_SYNASTRY_DAYS = 30L;
    private static final long TTL_TRANSIT_HOURS = 24L;

    // ─────────────────────── 依赖 ──────────────────────────────────────────

    private final AstrologyPythonClient pythonClient;
    private final AstrologyMemoryService astrologyMemoryService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public AstrologyGatewayService(AstrologyPythonClient pythonClient,
                                   AstrologyMemoryService astrologyMemoryService,
                                   RedisTemplate<String, Object> redisTemplate,
                                   @Qualifier("webObjectMapper") ObjectMapper objectMapper) {
        this.pythonClient = pythonClient;
        this.astrologyMemoryService = astrologyMemoryService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ─────────────────────── 本命盘 ───────────────────────────────────────

    /**
     * 计算（或读取缓存的）本命盘
     *
     * @param userId    当前用户 ID
     * @param request   前端请求
     * @return 本命盘结果
     */
    public NatalChartResponseDTO getNatalChart(Long userId, NatalRequestDTO request) {
        String cacheKey = KEY_NATAL_PREFIX + userId;

        // 尝试读取 Redis 缓存（本命盘永久缓存）
        NatalChartResponseDTO cached = getFromCache(cacheKey, NatalChartResponseDTO.class);
        if (cached != null) {
            log.debug("Natal chart cache hit: userId={}", userId);
            cached.setSavedToProfile(true);
            return cached;
        }

        // 未命中，调用 Python 服务
        NatalChartResponseDTO result = pythonClient.calculateNatal(request.getBirthInfo());

        // 保存到 Redis（本命盘永久缓存 = 不设 TTL）
        if (request.isSaveToProfile()) {
            saveToCache(cacheKey, result, null);
            result.setSavedToProfile(true);
            log.info("Natal chart saved to profile: userId={}", userId);
        }

        // 异步提取情绪标签
        astrologyMemoryService.extractAndSaveChartEmotionTagsAsync(userId, result.getChart(), "NATAL");

        return result;
    }

    /**
     * 强制刷新本命盘（用于用户重新录入出生信息）
     */
    public NatalChartResponseDTO refreshNatalChart(Long userId, NatalRequestDTO request) {
        String cacheKey = KEY_NATAL_PREFIX + userId;
        redisTemplate.delete(cacheKey);
        log.info("Natal chart cache cleared for userId={}", userId);
        return getNatalChart(userId, request);
    }

    // ─────────────────────── 和盘 ─────────────────────────────────────────

    /**
     * 计算（或读取缓存的）和盘
     *
     * @param userId  当前用户 ID
     * @param request 前端请求
     * @return 和盘结果
     */
    public SynastryResponseDTO getSynastryChart(Long userId, SynastryRequestDTO request) {
        String pairHash = buildSynastryHash(request);
        String cacheKey = KEY_SYNASTRY_PREFIX + pairHash;

        SynastryResponseDTO cached = getFromCache(cacheKey, SynastryResponseDTO.class);
        if (cached != null) {
            log.debug("Synastry chart cache hit: userId={}", userId);
            return cached;
        }

        SynastryResponseDTO result = pythonClient.calculateSynastry(
                request.getSelfBirthInfo(), request.getPartnerBirthInfo());

        // 缓存 30 天
        saveToCache(cacheKey, result, Duration.ofDays(TTL_SYNASTRY_DAYS));

        // 异步保存关系记录到 Memory
        astrologyMemoryService.saveRelationshipRecord(
                userId, request.getPartnerName(),
                request.getRelationshipType(), result.getThemes());

        // 异步提取情绪标签
        astrologyMemoryService.extractAndSaveChartEmotionTagsAsync(userId, result.getChart(), "SYNASTRY");

        return result;
    }

    // ─────────────────────── 流运 ─────────────────────────────────────────

    /**
     * 计算（或读取缓存的）流运
     *
     * @param userId  当前用户 ID
     * @param request 前端请求
     * @return 流运结果
     */
    public TransitResponseDTO getTransitChart(Long userId, TransitRequestDTO request) {
        String targetDate = StringUtils.hasText(request.getTargetDate())
                ? request.getTargetDate()
                : LocalDate.now().toString();
        String cacheKey = KEY_TRANSIT_PREFIX + userId + ":" + targetDate;

        TransitResponseDTO cached = getFromCache(cacheKey, TransitResponseDTO.class);
        if (cached != null) {
            log.debug("Transit chart cache hit: userId={}, date={}", userId, targetDate);
            return cached;
        }

        TransitResponseDTO result = pythonClient.calculateTransit(
                request.getBirthInfo(), targetDate, request.getWindowDays());

        // 缓存 24 小时
        saveToCache(cacheKey, result, Duration.ofHours(TTL_TRANSIT_HOURS));

        // 异步提取情绪标签（流运最重要）
        astrologyMemoryService.extractAndSaveChartEmotionTagsAsync(userId, result.getChart(), "TRANSIT");

        return result;
    }

    // ─────────────────────── 缓存工具 ─────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> T getFromCache(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) return null;
            // RedisTemplate 使用 Jackson 序列化，value 已经是目标类型（或 LinkedHashMap 需转换）
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            // 兜底：通过 ObjectMapper 转换
            return objectMapper.convertValue(value, type);
        } catch (Exception e) {
            log.warn("Cache read failed, key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void saveToCache(String key, Object value, Duration ttl) {
        try {
            if (ttl == null) {
                // 永久缓存（本命盘）
                redisTemplate.opsForValue().set(key, value);
            } else {
                redisTemplate.opsForValue().set(key, value, ttl.getSeconds(), TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Cache write failed, key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 构建和盘缓存 Key（基于双方出生信息 SHA-256 摘要，避免 hashCode 碰撞导致缓存污染）
     */
    private String buildSynastryHash(SynastryRequestDTO request) {
        BirthInfoDTO self = request.getSelfBirthInfo();
        BirthInfoDTO partner = request.getPartnerBirthInfo();
        // 使用出生信息的关键字段构建稳定字符串（A-B 与 B-A 方向有意义，不排序）
        String selfKey = self.getYear() + "_" + self.getMonth() + "_" + self.getDay() + "_"
                + self.getHour() + "_" + self.getMinute() + "_" + self.getCity();
        String partnerKey = partner.getYear() + "_" + partner.getMonth() + "_" + partner.getDay() + "_"
                + partner.getHour() + "_" + partner.getMinute() + "_" + partner.getCity();
        String combined = selfKey + "|" + partnerKey;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // 取前 16 字节（128 bit）转十六进制，足够避免碰撞
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 在 JVM 中必然存在，此分支不会触发；兜底回退使用 long 哈希
            return String.valueOf(Integer.toUnsignedLong(combined.hashCode()));
        }
    }

    /**
     * 查询用户是否有缓存的本命盘（前端用于判断是否需要重新录入出生信息）
     */
    public boolean hasNatalChart(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_NATAL_PREFIX + userId));
    }

    /**
     * 获取用户已缓存的本命盘（不触发重新计算）
     */
    public NatalChartResponseDTO getCachedNatalChart(Long userId) {
        return getFromCache(KEY_NATAL_PREFIX + userId, NatalChartResponseDTO.class);
    }

    /**
     * RAG 缓存 Key（由 AstrologyAiService 使用）
     *
     * @param queryHash query 的简单哈希值
     * @return Redis Key
     */
    public static String buildRagCacheKey(String queryHash) {
        return "rag:" + queryHash;
    }
}

