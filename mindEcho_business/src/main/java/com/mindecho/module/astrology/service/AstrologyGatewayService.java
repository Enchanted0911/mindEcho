package com.mindecho.module.astrology.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import com.mindecho.module.astrology.client.AstrologyPythonClient;
import com.mindecho.module.astrology.dto.*;
import com.mindecho.module.astrology.entity.UserAstrology;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 占星 Gateway 服务（Java → Python HTTP 路由层）
 *
 * <p>职责：
 * <ol>
 *   <li>将 Python 计算的星盘数据通过 Redis 缓存加速访问</li>
 *   <li>命中 Redis 缓存则直接返回，否则转发 Python 服务</li>
 *   <li>返回结构化星盘数据（不含 AI 解读）</li>
 *   <li>本命盘计算结果同时持久化到 user_astrology 表</li>
 * </ol>
 *
 * <p>UserAstrology 的 CRUD 和出生信息管理由 {@link UserAstrologyService} 负责，
 * 本服务只专注于星盘计算和缓存逻辑。
 *
 * <p>Redis 缓存策略（PRD §7）：
 * <ul>
 *   <li>{@code natal:{userId}}  — 永久（本命盘不变）</li>
 *   <li>{@code synastry:{userId}:{pairHash}} — 30 天</li>
 *   <li>{@code transit:{userId}:{date}} — 24 小时</li>
 * </ul>
 */
@Slf4j
@Service
public class AstrologyGatewayService {

    // ─────────────────────── Redis Key 前缀 & TTL ───────────────────────────

    private static final long TTL_SYNASTRY_DAYS = 30L;
    private static final long TTL_TRANSIT_HOURS = 24L;

    // ─────────────────────── 依赖 ──────────────────────────────────────────

    private final AstrologyPythonClient pythonClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserAstrologyService userAstrologyService;

    public AstrologyGatewayService(AstrologyPythonClient pythonClient,
                                   RedisTemplate<String, Object> redisTemplate,
                                   @Qualifier("webObjectMapper") ObjectMapper objectMapper,
                                   UserAstrologyService userAstrologyService) {
        this.pythonClient = pythonClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.userAstrologyService = userAstrologyService;
    }

    // ─────────────────────── 本命盘 ───────────────────────────────────────

    /**
     * 计算（或读取缓存/DB的）本命盘
     *
     * <p>优先级：Redis 缓存 → DB 缓存（user_astrology.natal_chart_data）→ Python 实时计算（并持久化）
     *
     * @param userId 当前用户 ID
     * @return 本命盘结果
     */
    public NatalChartResponseDTO getNatalChart(UUID userId) {
        String cacheKey = UserAstrologyService.KEY_NATAL_PREFIX + userId.toString();

        // 1. 尝试读取 Redis 缓存（本命盘永久缓存）
        NatalChartResponseDTO cached = getFromCache(cacheKey, NatalChartResponseDTO.class);
        if (cached != null) {
            log.debug("Natal chart Redis cache hit: userId={}", userId);
            cached.setSavedToProfile(true);
            return cached;
        }

        // 2. 查询 user_astrology 记录
        UserAstrology astrology = userAstrologyService.getOrInitAstrology(userId);

        // 3. 尝试读取 DB 缓存（natal_chart_data）
        if (StringUtils.hasText(astrology.getNatalChartData())) {
            try {
                NatalChartResponseDTO dbCached = objectMapper.readValue(
                        astrology.getNatalChartData(), NatalChartResponseDTO.class);
                dbCached.setSavedToProfile(true);
                // 回写 Redis 缓存，加速后续访问
                saveToCache(cacheKey, dbCached, null);
                log.debug("Natal chart DB cache hit: userId={}", userId);
                return dbCached;
            } catch (Exception e) {
                log.warn("Failed to parse natal_chart_data from DB, recalculating: userId={}, error={}",
                        userId, e.getMessage());
            }
        }

        // 4. 检查用户是否设置了出生信息
        if (!StringUtils.hasText(astrology.getBirthTime()) || !StringUtils.hasText(astrology.getBirthCity())) {
            throw new BusinessException(ResultCode.BIRTH_INFO_NOT_SET);
        }

        // 5. 从 user_astrology 表读取出生信息，构造 BirthInfoDTO
        BirthInfoDTO birthInfo = buildBirthInfoFromAstrology(astrology);

        // 6. 调用 Python 服务计算本命盘
        NatalChartResponseDTO result = pythonClient.calculateNatal(birthInfo);
        result.setSavedToProfile(true);

        // 7. 持久化到 user_astrology 表（DB 永久缓存）
        saveNatalChartToDb(userId, astrology, result);

        // 8. 保存到 Redis（本命盘永久缓存 = 不设 TTL）
        saveToCache(cacheKey, result, null);
        log.info("Natal chart calculated and saved: userId={}", userId);

        return result;
    }

    /**
     * 强制刷新本命盘（用于用户重新录入出生信息后触发）
     *
     * <p>清除 DB 缓存字段后再清除 Redis 缓存，最后重新从 user_astrology 表读取出生信息计算。
     *
     * <p>清除顺序：先写 DB（清空 natal_chart_data）→ 再删 Redis，
     * 避免出现"Redis 已清、DB 尚未清"的窗口期导致旧数据被并发请求重新写入 Redis 的问题。
     * 此规则与 {@link UserAstrologyService#updateBirthInfo} 等缓存清空方法保持一致。
     */
    public NatalChartResponseDTO refreshNatalChart(UUID userId) {
        // 1. 先检查出生信息是否存在，避免先清缓存再报错
        UserAstrology astrology = userAstrologyService.getOrInitAstrology(userId);
        if (!StringUtils.hasText(astrology.getBirthTime()) || !StringUtils.hasText(astrology.getBirthCity())) {
            throw new BusinessException(ResultCode.BIRTH_INFO_NOT_SET);
        }

        // 2. 先清除 DB 缓存字段（持久化），防止 Redis 清除后并发请求从 DB 读到旧数据再写回 Redis
        astrology.setNatalChartData(null);
        userAstrologyService.updateAstrology(astrology);

        // 3. 再清除 Redis 缓存（DB 已清，此时即使有并发请求也会直接走 Python 重新计算）
        String cacheKey = UserAstrologyService.KEY_NATAL_PREFIX + userId.toString();
        redisTemplate.delete(cacheKey);

        log.info("Natal chart cache cleared for userId={}", userId);
        return getNatalChart(userId);
    }

    // ─────────────────────── 和盘 ─────────────────────────────────────────

    /**
     * 计算（或读取缓存的）和盘（无参数版本）
     *
     * <p>自己和对方的出生信息均从 user_astrology 表读取（synastry_partner_* 字段）。
     * 若 user_astrology 表中没有对方出生信息，抛出 SYNASTRY_PARTNER_NOT_SET 错误（7002）。
     *
     * @param userId 当前用户 ID
     * @return 和盘结果
     */
    public SynastryResponseDTO getSynastryChart(UUID userId) {
        UserAstrology astrology = userAstrologyService.getOrInitAstrology(userId);

        // 检查自己的出生信息
        if (!StringUtils.hasText(astrology.getBirthTime()) || !StringUtils.hasText(astrology.getBirthCity())) {
            throw new BusinessException(ResultCode.BIRTH_INFO_NOT_SET);
        }
        // 检查对方出生信息
        if (!StringUtils.hasText(astrology.getSynastryPartnerTime())
                || !StringUtils.hasText(astrology.getSynastryPartnerCity())) {
            throw new BusinessException(ResultCode.SYNASTRY_PARTNER_NOT_SET);
        }

        // 从 DB 字段还原 SynastryRequestDTO
        SynastryRequestDTO request = buildSynastryRequestFromAstrology(astrology);
        return getSynastryChart(userId, request);
    }

    /**
     * 计算（或读取缓存的）和盘
     *
     * <p>自己的出生信息从 user_astrology 表读取，前端只需传对方的出生信息和名字。
     * 计算结果持久化到 user_astrology 表的 synastry_chart_data 字段。
     *
     * @param userId  当前用户 ID
     * @param request 前端请求（仅包含对方出生信息和名字）
     * @return 和盘结果
     */
    public SynastryResponseDTO getSynastryChart(UUID userId, SynastryRequestDTO request) {
        // 1. 从 user_astrology 表读取自己的出生信息
        UserAstrology astrology = userAstrologyService.getOrInitAstrology(userId);
        if (!StringUtils.hasText(astrology.getBirthTime()) || !StringUtils.hasText(astrology.getBirthCity())) {
            throw new BusinessException(ResultCode.BIRTH_INFO_NOT_SET);
        }
        BirthInfoDTO selfBirthInfo = buildBirthInfoFromAstrology(astrology);

        // 2. 构建缓存 key（基于双方出生信息）
        String pairHash = buildSynastryHash(selfBirthInfo, request.getPartnerBirthInfo());
        String cacheKey = UserAstrologyService.KEY_SYNASTRY_PREFIX + userId.toString() + ":" + pairHash;

        // 3. 尝试读取 Redis 缓存
        SynastryResponseDTO cached = getFromCache(cacheKey, SynastryResponseDTO.class);
        if (cached != null) {
            log.debug("Synastry chart Redis cache hit: userId={}", userId);
            return cached;
        }

        // 4. 尝试读取 DB 缓存（synastry_chart_data，按 pairHash 索引）
        if (StringUtils.hasText(astrology.getSynastryChartData())) {
            try {
                java.util.Map<?, ?> synastryMap = objectMapper.readValue(
                        astrology.getSynastryChartData(), java.util.Map.class);
                Object cachedEntry = synastryMap.get(pairHash);
                if (cachedEntry != null) {
                    SynastryResponseDTO dbCached = objectMapper.convertValue(cachedEntry, SynastryResponseDTO.class);
                    // 回写 Redis
                    saveToCache(cacheKey, dbCached, Duration.ofDays(TTL_SYNASTRY_DAYS));
                    log.debug("Synastry chart DB cache hit: userId={}, pairHash={}", userId, pairHash);
                    return dbCached;
                }
            } catch (Exception e) {
                log.warn("Failed to parse synastry_chart_data from DB: userId={}, error={}", userId, e.getMessage());
            }
        }

        // 5. 调用 Python 服务计算和盘
        SynastryResponseDTO result = pythonClient.calculateSynastry(
                selfBirthInfo, request.getPartnerBirthInfo());

        // 6. 持久化和盘结果和对方出生信息到 user_astrology 表（合并为单次写操作）
        saveSynastryChartAndPartnerToDb(userId, astrology, pairHash, result, request);

        // 7. 缓存 30 天
        saveToCache(cacheKey, result, Duration.ofDays(TTL_SYNASTRY_DAYS));

        log.info("Synastry chart calculated and saved: userId={}, partner={}", userId, request.getPartnerName());
        return result;
    }

    // ─────────────────────── 流运 ─────────────────────────────────────────

    /**
     * 计算（或读取缓存的）流运
     *
     * <p>出生信息从 user_astrology 表读取，前端只需传查询日期（可选）。
     * 计算结果同时持久化到 user_astrology 表（transit_chart_data 字段）。
     *
     * @param userId  当前用户 ID
     * @param request 前端请求（仅 targetDate/windowDays）
     * @return 流运结果
     */
    public TransitResponseDTO getTransitChart(UUID userId, TransitRequestDTO request) {
        String targetDate = StringUtils.hasText(request.getTargetDate())
                ? request.getTargetDate()
                : LocalDate.now().toString();
        String cacheKey = UserAstrologyService.KEY_TRANSIT_PREFIX + userId.toString() + ":" + targetDate;

        // 1. 尝试读取 Redis 缓存（24 小时）
        TransitResponseDTO cached = getFromCache(cacheKey, TransitResponseDTO.class);
        if (cached != null) {
            log.debug("Transit chart cache hit: userId={}, date={}", userId, targetDate);
            return cached;
        }

        // 2. 从 user_astrology 表读取出生信息和 DB 缓存
        UserAstrology astrology = userAstrologyService.getOrInitAstrology(userId);
        if (!StringUtils.hasText(astrology.getBirthTime()) || !StringUtils.hasText(astrology.getBirthCity())) {
            throw new BusinessException(ResultCode.BIRTH_INFO_NOT_SET);
        }

        // 3. 尝试读取 DB 缓存（transit_chart_data，按 date 索引）
        if (StringUtils.hasText(astrology.getTransitChartData())) {
            try {
                TransitResponseDTO fromDb = deserializeTransitFromDb(astrology.getTransitChartData(), targetDate);
                if (fromDb != null) {
                    saveToCache(cacheKey, fromDb, Duration.ofHours(TTL_TRANSIT_HOURS));
                    log.debug("Transit chart DB cache hit: userId={}, date={}", userId, targetDate);
                    return fromDb;
                }
            } catch (Exception e) {
                log.warn("Failed to parse transit_chart_data from DB: userId={}, error={}", userId, e.getMessage());
            }
        }

        BirthInfoDTO birthInfo = buildBirthInfoFromAstrology(astrology);

        // 4. 调用 Python 服务计算流运
        TransitResponseDTO result = pythonClient.calculateTransit(
                birthInfo, targetDate, request.getWindowDays());

        // 5. 持久化最新流运结果和目标日期到 user_astrology 表（合并为单次写操作）
        saveTransitChartAndDateToDb(userId, astrology, targetDate, result);

        // 6. 缓存 24 小时
        saveToCache(cacheKey, result, Duration.ofHours(TTL_TRANSIT_HOURS));

        log.info("Transit chart calculated and saved: userId={}, date={}", userId, targetDate);
        return result;
    }

    // ─────────────────────── 查询辅助 ─────────────────────────────────────

    /**
     * 查询用户是否有本命盘数据（Redis 缓存或 DB 缓存）
     */
    public boolean hasNatalChart(UUID userId) {
        // 先检查 Redis
        if (Boolean.TRUE.equals(redisTemplate.hasKey(UserAstrologyService.KEY_NATAL_PREFIX + userId.toString()))) {
            return true;
        }
        // 再检查 DB
        UserAstrology astrology = userAstrologyService.getOrInitAstrology(userId);
        return StringUtils.hasText(astrology.getNatalChartData());
    }

    /**
     * 获取用户已缓存的本命盘（不触发重新计算）
     */
    public NatalChartResponseDTO getCachedNatalChart(UUID userId) {
        // 先查 Redis
        NatalChartResponseDTO cached = getFromCache(
                UserAstrologyService.KEY_NATAL_PREFIX + userId.toString(), NatalChartResponseDTO.class);
        if (cached != null) return cached;
        // 再查 DB
        UserAstrology astrology = userAstrologyService.getOrInitAstrology(userId);
        if (StringUtils.hasText(astrology.getNatalChartData())) {
            try {
                return objectMapper.readValue(astrology.getNatalChartData(), NatalChartResponseDTO.class);
            } catch (Exception e) {
                log.warn("Failed to parse natal_chart_data from DB: userId={}", userId);
            }
        }
        return null;
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

    // ─────────────────────── 辅助：从 UserAstrology 构建 BirthInfoDTO ──────

    /**
     * 从 user_astrology 表字段构建 BirthInfoDTO
     *
     * <p>birth_time 格式：yyyy-MM-dd HH:mm
     */
    private BirthInfoDTO buildBirthInfoFromAstrology(UserAstrology astrology) {
        BirthInfoDTO dto = new BirthInfoDTO();
        try {
            String birthTime = astrology.getBirthTime(); // yyyy-MM-dd HH:mm
            String[] parts = birthTime.split(" ");
            String[] dateParts = parts[0].split("-");
            String[] timeParts = parts[1].split(":");
            dto.setYear(Integer.parseInt(dateParts[0]));
            dto.setMonth(Integer.parseInt(dateParts[1]));
            dto.setDay(Integer.parseInt(dateParts[2]));
            dto.setHour(Integer.parseInt(timeParts[0]));
            dto.setMinute(Integer.parseInt(timeParts[1]));
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BIRTH_INFO_NOT_SET);
        }
        dto.setCity(astrology.getBirthCity());
        dto.setLatitude(astrology.getBirthLat());
        dto.setLongitude(astrology.getBirthLng());
        // 默认时区 Asia/Shanghai（支持多区域用户时可由前端扩展传入 timezone 字段覆盖）
        dto.setTimezone("Asia/Shanghai");
        return dto;
    }

    /**
     * 从 user_astrology 表字段构建 SynastryRequestDTO（还原上次和盘对方信息）
     */
    private SynastryRequestDTO buildSynastryRequestFromAstrology(UserAstrology astrology) {
        SynastryRequestDTO req = new SynastryRequestDTO();
        req.setPartnerName(StringUtils.hasText(astrology.getSynastryPartnerName())
                ? astrology.getSynastryPartnerName() : "Ta");
        BirthInfoDTO partner = new BirthInfoDTO();
        try {
            String partnerTime = astrology.getSynastryPartnerTime(); // yyyy-MM-dd HH:mm
            String[] parts = partnerTime.split(" ");
            String[] dateParts = parts[0].split("-");
            String[] timeParts = parts[1].split(":");
            partner.setYear(Integer.parseInt(dateParts[0]));
            partner.setMonth(Integer.parseInt(dateParts[1]));
            partner.setDay(Integer.parseInt(dateParts[2]));
            partner.setHour(Integer.parseInt(timeParts[0]));
            partner.setMinute(timeParts.length > 1 ? Integer.parseInt(timeParts[1]) : 0);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SYNASTRY_PARTNER_NOT_SET);
        }
        partner.setCity(astrology.getSynastryPartnerCity());
        partner.setLatitude(astrology.getSynastryPartnerLat());
        partner.setLongitude(astrology.getSynastryPartnerLng());
        req.setPartnerBirthInfo(partner);
        return req;
    }

    // ─────────────────────── 辅助：持久化本命盘到 DB ─────────────────────

    private void saveNatalChartToDb(UUID userId, UserAstrology astrology, NatalChartResponseDTO result) {
        try {
            astrology.setNatalChartData(objectMapper.writeValueAsString(result));
            userAstrologyService.updateAstrology(astrology);
            log.info("Natal chart saved to DB: userId={}", userId);
        } catch (Exception e) {
            log.warn("Failed to save natal chart to DB: userId={}, error={}", userId, e.getMessage());
        }
    }

    // ─────────────────────── 辅助：持久化和盘到 DB ───────────────────────

    /**
     * 持久化和盘结果和对方出生信息到 user_astrology 表（合并为单次写操作）
     *
     * <p>同时保存 synastry_chart_data（JSON Map，最多保留 5 条）和对方出生信息字段，
     * 避免原先拆分为两次 updateAstrology 导致的重复写 DB 问题。
     */
    @SuppressWarnings("unchecked")
    private void saveSynastryChartAndPartnerToDb(UUID userId, UserAstrology astrology,
                                                 String pairHash, SynastryResponseDTO result,
                                                 SynastryRequestDTO request) {
        try {
            // 更新 synastry_chart_data（按 pairHash 存储最近 5 条）
            java.util.Map<String, Object> synastryMap = new java.util.LinkedHashMap<>();
            if (StringUtils.hasText(astrology.getSynastryChartData())) {
                try {
                    synastryMap = objectMapper.readValue(
                            astrology.getSynastryChartData(),
                            new TypeReference<java.util.LinkedHashMap<String, Object>>() {});
                } catch (Exception ignored) { /* 解析失败则重建 */ }
            }
            synastryMap.put(pairHash, result);
            while (synastryMap.size() > 5) {
                synastryMap.remove(synastryMap.keySet().iterator().next());
            }
            astrology.setSynastryChartData(objectMapper.writeValueAsString(synastryMap));

            // 更新对方出生信息（前端回填用）
            BirthInfoDTO partner = request.getPartnerBirthInfo();
            astrology.setSynastryPartnerName(request.getPartnerName());
            astrology.setSynastryPartnerCity(partner.getCity());
            astrology.setSynastryPartnerLat(partner.getLatitude());
            astrology.setSynastryPartnerLng(partner.getLongitude());
            astrology.setSynastryPartnerTime(String.format("%04d-%02d-%02d %02d:%02d",
                    partner.getYear(), partner.getMonth(), partner.getDay(),
                    partner.getHour(), partner.getMinute()));

            userAstrologyService.updateAstrology(astrology);
            log.info("Synastry chart and partner info saved to DB: userId={}, pairHash={}", userId, pairHash);
        } catch (Exception e) {
            log.warn("Failed to save synastry chart and partner to DB: userId={}, error={}", userId, e.getMessage());
        }
    }

    // ─────────────────────── 辅助：持久化流运到 DB ───────────────────────

    /**
     * 持久化流运结果和目标日期到 user_astrology 表（合并为单次写操作）
     *
     * <p>同时保存 transit_chart_data（JSON）和 transit_target_date（前端回填用），
     * 避免原先拆分为两次 updateAstrology 导致的重复写 DB 问题。
     */
    private void saveTransitChartAndDateToDb(UUID userId, UserAstrology astrology,
                                             String targetDate, TransitResponseDTO result) {
        try {
            // 优先使用 DTO 中解析出的 date 字段，若无则用 targetDate 参数
            String effectiveDate = org.springframework.util.StringUtils.hasText(result.getDate())
                    ? result.getDate() : targetDate;
            java.util.Map<String, Object> wrapper = new java.util.LinkedHashMap<>();
            wrapper.put("date", effectiveDate);
            wrapper.put("events", result.getEvents());
            wrapper.put("summary", result.getSummary());
            astrology.setTransitChartData(objectMapper.writeValueAsString(wrapper));
            astrology.setTransitTargetDate(effectiveDate);
            userAstrologyService.updateAstrology(astrology);
            log.info("Transit chart and date saved to DB: userId={}, date={}", userId, effectiveDate);
        } catch (Exception e) {
            log.warn("Failed to save transit chart to DB: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 从 transit_chart_data JSON 反序列化流运 DTO（DB 缓存命中时调用）
     *
     * <p>仅当缓存 JSON 中的 date 字段与目标日期匹配时才返回数据，
     * 否则返回 null 表示缓存未命中（日期不同，需重新计算）。
     *
     * @param transitChartJson user_astrology.transit_chart_data 字段值
     * @param targetDate       期望命中的日期（yyyy-MM-dd）
     * @return 命中时返回 {@link TransitResponseDTO}，未命中时返回 null
     */
    @SuppressWarnings("unchecked")
    private TransitResponseDTO deserializeTransitFromDb(String transitChartJson, String targetDate) {
        try {
            java.util.Map<String, Object> wrapper = objectMapper.readValue(
                    transitChartJson,
                    new TypeReference<java.util.LinkedHashMap<String, Object>>() {});
            // 校验日期是否匹配
            if (!targetDate.equals(wrapper.get("date"))) {
                return null;
            }
            // 反序列化 events（List<JsonNode>）
            List<JsonNode> events = new ArrayList<>();
            Object eventsObj = wrapper.get("events");
            if (eventsObj instanceof List<?> list) {
                for (Object item : list) {
                    events.add(objectMapper.valueToTree(item));
                }
            }
            // 反序列化 summary（JsonNode）
            JsonNode summary = wrapper.get("summary") != null
                    ? objectMapper.valueToTree(wrapper.get("summary")) : null;
            // 读取持久化的 date 字段
            String dateStr = wrapper.get("date") != null ? String.valueOf(wrapper.get("date")) : targetDate;
            return TransitResponseDTO.builder()
                    .date(dateStr)
                    .events(events)
                    .summary(summary)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to deserialize transit chart from DB: {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────── 缓存工具 ─────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> T getFromCache(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) return null;
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            return objectMapper.convertValue(value, type);
        } catch (Exception e) {
            log.warn("Cache read failed, key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void saveToCache(String key, Object value, Duration ttl) {
        try {
            if (ttl == null) {
                redisTemplate.opsForValue().set(key, value);
            } else {
                redisTemplate.opsForValue().set(key, value, ttl.getSeconds(), TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Cache write failed, key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 构建和盘缓存 Key（基于双方出生信息 SHA-256 摘要）
     */
    private String buildSynastryHash(BirthInfoDTO self, BirthInfoDTO partner) {
        String selfKey = self.getYear() + "_" + self.getMonth() + "_" + self.getDay() + "_"
                + self.getHour() + "_" + self.getMinute() + "_" + self.getCity();
        String partnerKey = partner.getYear() + "_" + partner.getMonth() + "_" + partner.getDay() + "_"
                + partner.getHour() + "_" + partner.getMinute() + "_" + partner.getCity();
        String combined = selfKey + "|" + partnerKey;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return String.valueOf(Integer.toUnsignedLong(combined.hashCode()));
        }
    }
}

