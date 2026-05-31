package com.mindecho.module.astrology.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import com.mindecho.module.astrology.entity.UserAstrology;
import com.mindecho.module.astrology.mapper.UserAstrologyMapper;
import com.mindecho.module.auth.dto.LoginResponse;
import com.mindecho.module.auth.dto.UpdateBirthInfoRequest;
import com.mindecho.module.auth.dto.UpdateSynastryPartnerRequest;
import com.mindecho.module.auth.dto.UpdateTransitDateRequest;
import com.mindecho.module.auth.entity.User;
import com.mindecho.module.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 用户星盘信息服务
 *
 * <p>职责：
 * <ul>
 *   <li>管理 user_astrology 表的 CRUD（getOrInit / update）</li>
 *   <li>出生信息更新（同步清除本命盘/和盘/流运缓存）</li>
 *   <li>和盘对方信息更新（同步清除和盘缓存）</li>
 *   <li>流运目标日期更新（同步清除流运缓存）</li>
 *   <li>组装用户信息响应 DTO（供 AuthService 使用）</li>
 * </ul>
 *
 * <p>Redis 缓存 Key 规范与 {@link AstrologyGatewayService} 保持一致：
 * <ul>
 *   <li>{@code natal:{userId}} — 本命盘永久缓存</li>
 *   <li>{@code synastry:{userId}:*} — 和盘 30 天缓存</li>
 *   <li>{@code transit:{userId}:*} — 流运 24 小时缓存</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAstrologyService {

    // ─── Redis Key 前缀（与 AstrologyGatewayService 保持一致） ──────────────
    static final String KEY_NATAL_PREFIX    = "natal:";
    static final String KEY_SYNASTRY_PREFIX = "synastry:";
    static final String KEY_TRANSIT_PREFIX  = "transit:";

    private final UserAstrologyMapper userAstrologyMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    // ─── UserAstrology CRUD ─────────────────────────────────────────────────

    /**
     * 获取用户星盘记录，若不存在则自动创建空记录。
     *
     * @param userId 用户 ID
     * @return 保证非空的 UserAstrology 实体
     */
    public UserAstrology getOrInitAstrology(UUID userId) {
        UserAstrology astrology = userAstrologyMapper.selectOne(
                new LambdaQueryWrapper<UserAstrology>()
                        .eq(UserAstrology::getUserId, userId)
                        .eq(UserAstrology::getDeleted, 0)
        );
        if (astrology == null) {
            astrology = new UserAstrology();
            astrology.setUserId(userId);
            userAstrologyMapper.insert(astrology);
            log.debug("UserAstrology record created for userId={}", userId);
        }
        return astrology;
    }

    /**
     * 更新 user_astrology 记录。
     *
     * @param astrology 待更新的 UserAstrology 实体（id 必须不为空）
     */
    public void updateAstrology(UserAstrology astrology) {
        userAstrologyMapper.updateById(astrology);
    }

    // ─── 出生信息更新 ────────────────────────────────────────────────────────

    /**
     * 更新用户出生信息，并清除所有相关星盘缓存（DB + Redis）。
     *
     * <p>出生信息变更后，本命盘、和盘、流运的 chart data 及 interpretation 均失效，
     * 一并清空以便下次重新计算。
     *
     * @param userId  当前用户 ID
     * @param request 出生信息更新请求
     * @return 更新后的用户信息 DTO
     */
    public LoginResponse.UserInfoDTO updateBirthInfo(UUID userId, UpdateBirthInfoRequest request) {
        User user = requireUser(userId);

        UserAstrology astrology = getOrInitAstrology(userId);
        astrology.setBirthCity(request.getBirthCity());
        astrology.setBirthLat(request.getBirthLat());
        astrology.setBirthLng(request.getBirthLng());
        astrology.setBirthTime(request.getBirthTime());
        // 出生信息变更 → 清空本命盘、和盘、流运的 DB 缓存字段
        astrology.setNatalChartData(null);
        astrology.setNatalChartSummary(null);
        astrology.setSynastryChartData(null);
        astrology.setTransitChartData(null);
        // transit_target_date 与 transit_chart_data 配套，chart 已清则目标日期一并清空，
        // 避免前端 transitTargetDate 回填值与已不存在的 chart 数据不匹配
        astrology.setTransitTargetDate(null);
        // 同时清空历史解读缓存（出生信息变了，旧解读无效）
        astrology.setNatalInterpretation(null);
        astrology.setSynastryInterpretation(null);
        astrology.setTransitInterpretation(null);
        updateAstrology(astrology);

        // 清空 Redis 本命盘缓存（永久缓存，直接删除）
        redisTemplate.delete(KEY_NATAL_PREFIX + userId.toString());

        // 清空 Redis 和盘缓存（前缀 synastry:{userId}:*）
        clearRedisKeysByPattern(KEY_SYNASTRY_PREFIX + userId + ":*");

        // 清空 Redis 流运缓存（前缀 transit:{userId}:*）
        clearRedisKeysByPattern(KEY_TRANSIT_PREFIX + userId + ":*");

        log.info("Birth info updated for userId={}, city={}, all chart caches cleared",
                userId, request.getBirthCity());
        return buildUserInfoDTO(user, astrology);
    }

    // ─── 和盘对方信息更新 ────────────────────────────────────────────────────

    /**
     * 保存和盘对方出生信息，并清除和盘相关缓存。
     *
     * <p>对方信息变更后，已有和盘 chart data 和 interpretation 均失效，
     * 清空后便于下次重新计算。
     *
     * @param userId  当前用户 ID
     * @param request 对方出生信息更新请求
     * @return 更新后的用户信息 DTO
     */
    public LoginResponse.UserInfoDTO updateSynastryPartner(UUID userId, UpdateSynastryPartnerRequest request) {
        User user = requireUser(userId);

        UserAstrology astrology = getOrInitAstrology(userId);
        astrology.setSynastryPartnerName(request.getPartnerName());
        astrology.setSynastryPartnerCity(request.getPartnerCity());
        astrology.setSynastryPartnerLat(request.getPartnerLat());
        astrology.setSynastryPartnerLng(request.getPartnerLng());
        astrology.setSynastryPartnerTime(request.getPartnerTime());
        // 对方信息变更 → 清空和盘 DB 缓存
        astrology.setSynastryChartData(null);
        astrology.setSynastryInterpretation(null);
        updateAstrology(astrology);

        // 清空 Redis 和盘缓存（pairHash 变了，旧缓存全部失效）
        clearRedisKeysByPattern(KEY_SYNASTRY_PREFIX + userId + ":*");

        log.info("Synastry partner updated for userId={}, partnerCity={}, synastry cache cleared",
                userId, request.getPartnerCity());
        return buildUserInfoDTO(user, astrology);
    }

    // ─── 流运目标日期更新 ────────────────────────────────────────────────────

    /**
     * 保存流运目标日期，并清除流运相关缓存。
     *
     * @param userId  当前用户 ID
     * @param request 流运目标日期更新请求
     * @return 更新后的用户信息 DTO
     */
    public LoginResponse.UserInfoDTO updateTransitDate(UUID userId, UpdateTransitDateRequest request) {
        User user = requireUser(userId);

        UserAstrology astrology = getOrInitAstrology(userId);
        astrology.setTransitTargetDate(request.getTargetDate());
        // 流运日期变更 → 清空流运 DB 缓存
        astrology.setTransitChartData(null);
        astrology.setTransitInterpretation(null);
        updateAstrology(astrology);

        // 清空 Redis 流运缓存（日期变了，旧缓存全部失效）
        clearRedisKeysByPattern(KEY_TRANSIT_PREFIX + userId + ":*");

        log.info("Transit date updated for userId={}, date={}, transit cache cleared",
                userId, request.getTargetDate());
        return buildUserInfoDTO(user, astrology);
    }

    // ─── UserInfoDTO 组装 ────────────────────────────────────────────────────

    /**
     * 组装用户信息 DTO（含出生信息、和盘对方信息、流运日期）。
     *
     * @param user     用户实体
     * @param astrology 用户星盘实体（可为 null）
     * @return 用户信息 DTO
     */
    public LoginResponse.UserInfoDTO buildUserInfoDTO(User user, UserAstrology astrology) {
        return LoginResponse.UserInfoDTO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .isVip(user.isVip())
                .aiPersonality(user.getAiPersonality())
                .vipExpireTime(user.getVipExpireTime() != null
                        ? user.getVipExpireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        : null)
                // 出生信息（来自 user_astrology 表）
                .birthCity(astrology != null ? astrology.getBirthCity() : null)
                .birthLat(astrology != null ? astrology.getBirthLat() : null)
                .birthLng(astrology != null ? astrology.getBirthLng() : null)
                .birthTime(astrology != null ? astrology.getBirthTime() : null)
                // 和盘对方信息（前端回填用）
                .synastryPartnerName(astrology != null ? astrology.getSynastryPartnerName() : null)
                .synastryPartnerCity(astrology != null ? astrology.getSynastryPartnerCity() : null)
                .synastryPartnerLat(astrology != null ? astrology.getSynastryPartnerLat() : null)
                .synastryPartnerLng(astrology != null ? astrology.getSynastryPartnerLng() : null)
                .synastryPartnerTime(astrology != null ? astrology.getSynastryPartnerTime() : null)
                // 流运目标日期（前端回填用）
                .transitTargetDate(astrology != null ? astrology.getTransitTargetDate() : null)
                .build();
    }

    // ─── 私有辅助方法 ─────────────────────────────────────────────────────────

    private User requireUser(UUID userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    /**
     * 使用 SCAN 命令（非阻塞）批量删除匹配 pattern 的 Redis Key。
     *
     * <p>使用 SCAN 而非 KEYS 的原因：
     * <ul>
     *   <li>{@code KEYS pattern} 会全量扫描整个 Redis keyspace，阻塞服务端，在 Key 数量庞大时
     *       可能引发延迟甚至超时，生产环境严禁使用。</li>
     *   <li>{@code SCAN} 以游标方式分批次扫描，每次迭代仅处理少量 Key，不会阻塞其他命令，
     *       适合在生产环境中做模式匹配删除。</li>
     * </ul>
     *
     * @param pattern Redis Key 模式（支持通配符 *），例如 {@code synastry:userId:*}
     */
    private void clearRedisKeysByPattern(String pattern) {
        try {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)   // 每批扫描 100 个槽位（非精确，Redis 可能多返也可能少返）
                    .build();
            List<String> keysToDelete = new ArrayList<>();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keysToDelete.add(cursor.next());
                }
            }
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
                log.debug("Deleted {} Redis keys matching pattern={}", keysToDelete.size(), pattern);
            }
        } catch (Exception e) {
            log.warn("Failed to clear Redis keys by pattern={}: {}", pattern, e.getMessage());
        }
    }

    // ─── 异步解读文本持久化（由 AstrologyAiService 调用，单独抽出以保证 @Async 生效） ──

    /**
     * 异步保存本命盘 AI 解读文本到 user_astrology.natal_interpretation
     *
     * <p>必须由外部 Spring Bean 调用，不可在 AstrologyAiService 内部通过 this 调用，
     * 否则 Spring AOP 代理失效导致 @Async 注解不生效。
     */
    @Async
    public void saveNatalInterpretationAsync(UUID userId, String interpretation) {
        if (!StringUtils.hasText(interpretation)) return;
        try {
            UserAstrology astrology = getOrInitAstrology(userId);
            astrology.setNatalInterpretation(interpretation);
            updateAstrology(astrology);
            log.debug("Natal interpretation saved: userId={}", userId);
        } catch (Exception e) {
            log.warn("Failed to save natal interpretation: userId={}", userId, e);
        }
    }

    /**
     * 异步保存和盘 AI 解读文本到 user_astrology.synastry_interpretation
     */
    @Async
    public void saveSynastryInterpretationAsync(UUID userId, String interpretation) {
        if (!StringUtils.hasText(interpretation)) return;
        try {
            UserAstrology astrology = getOrInitAstrology(userId);
            astrology.setSynastryInterpretation(interpretation);
            updateAstrology(astrology);
            log.debug("Synastry interpretation saved: userId={}", userId);
        } catch (Exception e) {
            log.warn("Failed to save synastry interpretation: userId={}", userId, e);
        }
    }

    /**
     * 异步保存流运 AI 解读文本到 user_astrology.transit_interpretation
     */
    @Async
    public void saveTransitInterpretationAsync(UUID userId, String interpretation) {
        if (!StringUtils.hasText(interpretation)) return;
        try {
            UserAstrology astrology = getOrInitAstrology(userId);
            astrology.setTransitInterpretation(interpretation);
            updateAstrology(astrology);
            log.debug("Transit interpretation saved: userId={}", userId);
        } catch (Exception e) {
            log.warn("Failed to save transit interpretation: userId={}", userId, e);
        }
    }
}

