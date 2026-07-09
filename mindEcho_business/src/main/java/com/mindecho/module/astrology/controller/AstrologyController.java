package com.mindecho.module.astrology.controller;

import com.mindecho.common.result.Result;
import com.mindecho.common.util.UserContext;
import com.mindecho.module.astrology.dto.*;
import com.mindecho.module.astrology.service.AstrologyAiService;
import com.mindecho.module.astrology.service.AstrologyGatewayService;
import com.mindecho.module.astrology.service.UserAstrologyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 占星模块 REST 接口
 *
 * <p>接口清单：
 * <pre>
 * GET  /api/astrology/info               一次获取完整 userAstrology 信息（登录/首屏调用）
 * POST /api/astrology/natal              单星盘计算（从 user 表读取出生信息，转发 Python）
 * POST /api/astrology/natal/interpret    单盘 AI 解读（chart 从 DB 读取，需先计算本命盘）
 * POST /api/astrology/synastry           和盘计算（从 user 表读取自己和对方出生信息）
 * POST /api/astrology/synastry/interpret 和盘 AI 解读（只传 relationshipType，chart 从 DB 读取）
 * POST /api/astrology/transit            流运计算（从 user 表读取出生信息，出生信息由后端读取）
 * POST /api/astrology/transit/interpret  流运 AI 解读（只传 windowDays，chart 从 DB 读取）
 * GET  /api/astrology/natal/check        查询当前用户是否有缓存本命盘
 * DELETE /api/astrology/natal/cache      强制清除并刷新本命盘缓存
 * </pre>
 *
 * <p>认证：所有接口均需 JWT Token（由 {@code JwtInterceptor} 拦截验证）。
 *
 * <p>前置条件说明：
 * <ul>
 *   <li>计算接口前置条件：user 表中已有对应的出生信息（birth_time / birth_city）</li>
 *   <li>和盘计算额外前置条件：user 表中已有对方出生信息（synastry_partner_*）</li>
 *   <li>解读接口前置条件：user 表中已有对应的星盘数据（natal_chart_data / synastry_chart_data）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/astrology")
@RequiredArgsConstructor
public class AstrologyController {

    private final AstrologyGatewayService gatewayService;
    private final AstrologyAiService aiService;
    private final UserAstrologyService userAstrologyService;

    // ─────────────────────── 星盘信息汇总 ────────────────────────────────

    /**
     * 一次获取完整 userAstrology 信息（登录/首屏调用）
     *
     * <p>返回用户出生信息、各类星盘缓存标志位、和盘对方信息、流运目标日期。
     * 前端登录成功后应调用此接口，将结果缓存到 store，避免进入各星盘页时重复弹出设置表单。
     *
     * GET /api/astrology/info
     */
    @GetMapping("/info")
    public Result<UserAstrologyInfoDTO> getAstrologyInfo() {
        UUID userId = UserContext.getUserId();
        log.info("Get astrology info: userId={}", userId);
        UserAstrologyInfoDTO info = userAstrologyService.getUserAstrologyInfo(userId);
        return Result.success(info);
    }

    // ─────────────────────── 单星盘（本命盘）─────────────────────────────

    /**
     * 计算本命盘（含缓存）
     *
     * <p>无需传出生信息，后端从 user 表读取；
     * 若用户未设置出生信息，返回 7001 错误，前端应引导用户先设置出生信息。
     * 计算结果同时持久化到 user 表的 natal_chart_data 字段（summary 信息包含在其中）。
     *
     * POST /api/astrology/natal
     */
    @PostMapping("/natal")
    public Result<NatalChartResponseDTO> calculateNatal() {
        UUID userId = UserContext.getUserId();
        log.info("Calculate natal chart: userId={}", userId);
        NatalChartResponseDTO result = gatewayService.getNatalChart(userId);
        log.info("Calculate natal chart result: userId={}, result={}", userId, result);
        return Result.success(result);
    }

    /**
     * 单盘 AI 解读
     *
     * <p>chart 数据由后端从 user 表 natal_chart_data 字段读取，前端无需传 chart。
     * 前置条件：用户已计算过本命盘（natal_chart_data 不为空），否则返回 7005 错误。
     *
     * POST /api/astrology/natal/interpret
     */
    @PostMapping("/natal/interpret")
    public Result<AstrologyInterpretResponseDTO> interpretNatal(
            @RequestBody(required = false) AstrologyInterpretRequestDTO request) {
        UUID userId = UserContext.getUserId();
        if (request == null) {
            request = new AstrologyInterpretRequestDTO();
        }
        log.info("Interpret natal chart: userId={}, focus={}", userId, request.getFocus());
        request.setInterpretType("NATAL");
        AstrologyInterpretResponseDTO result = aiService.interpretNatal(userId, request);
        log.info("Interpret natal chart result: userId={}, result={}", userId, result);
        return Result.success(result);
    }

    /**
     * 查询当前用户是否有缓存的本命盘
     *
     * GET /api/astrology/natal/check
     */
    @GetMapping("/natal/check")
    public Result<Boolean> checkNatalChart() {
        UUID userId = UserContext.getUserId();
        boolean hasChart = gatewayService.hasNatalChart(userId);
        return Result.success(hasChart);
    }

    /**
     * 强制刷新本命盘（清除 Redis 缓存和 DB 缓存后重新计算）
     *
     * <p>适用场景：用户修改出生信息后，后端已自动清空缓存；
     * 此接口用于手动强制刷新。
     *
     * DELETE /api/astrology/natal/cache
     */
    @DeleteMapping("/natal/cache")
    public Result<NatalChartResponseDTO> refreshNatalChart() {
        UUID userId = UserContext.getUserId();
        log.info("Refresh natal chart: userId={}", userId);
        NatalChartResponseDTO result = gatewayService.refreshNatalChart(userId);
        return Result.success("本命盘已重新计算", result);
    }

    // ─────────────────────── 和盘（合盘）────────────────────────────────

    /**
     * 计算和盘（含 30 天缓存）
     *
     * <p>无需传任何参数，后端从 user 表读取自己的出生信息和上次保存的对方出生信息。
     * 若 user 表中没有对方出生信息，返回 7002 错误，前端应引导用户先设置对方信息。
     *
     * POST /api/astrology/synastry
     */
    @PostMapping("/synastry")
    public Result<SynastryResponseDTO> calculateSynastry() {
        UUID userId = UserContext.getUserId();
        log.info("Calculate synastry: userId={}", userId);
        SynastryResponseDTO result = gatewayService.getSynastryChart(userId);
        return Result.success(result);
    }

    /**
     * 和盘 AI 解读
     *
     * <p>chart 数据由后端从 user 表 synastry_chart_data 字段读取，前端只需传 relationshipType。
     * 前置条件：用户已计算过和盘（synastry_chart_data 不为空），否则返回 7003 错误。
     *
     * POST /api/astrology/synastry/interpret
     */
    @PostMapping("/synastry/interpret")
    public Result<AstrologyInterpretResponseDTO> interpretSynastry(
            @RequestBody(required = false) SynastryInterpretRequestDTO request) {
        UUID userId = UserContext.getUserId();
        if (request == null) {
            request = new SynastryInterpretRequestDTO();
        }
        log.info("Interpret synastry chart: userId={}, relationshipType={}", userId, request.getRelationshipType());
        AstrologyInterpretResponseDTO result = aiService.interpretSynastry(userId, request);
        return Result.success(result);
    }

    // ─────────────────────── 流运（过境）────────────────────────────────

    /**
     * 计算流运（含 24 小时缓存）
     *
     * <p>出生信息从 user 表读取，前端只需传 targetDate（可选）和 windowDays（可选）。
     *
     * POST /api/astrology/transit
     */
    @PostMapping("/transit")
    public Result<TransitResponseDTO> calculateTransit(@RequestBody(required = false) TransitRequestDTO request) {
        UUID userId = UserContext.getUserId();
        if (request == null) {
            request = new TransitRequestDTO();
        }
        log.info("Calculate transit: userId={}, targetDate={}", userId, request.getTargetDate());
        TransitResponseDTO result = gatewayService.getTransitChart(userId, request);
        return Result.success(result);
    }

    /**
     * 流运 AI 解读
     *
     * <p>chart 数据由后端从 user 表读取，前端只需传 windowDays（时间窗口）。
     * 前置条件：用户已计算过流运（synastry_chart_summary 中有流运数据），否则返回 7004 错误。
     *
     * POST /api/astrology/transit/interpret
     */
    @PostMapping("/transit/interpret")
    public Result<AstrologyInterpretResponseDTO> interpretTransit(
            @RequestBody(required = false) TransitInterpretRequestDTO request) {
        UUID userId = UserContext.getUserId();
        if (request == null) {
            request = new TransitInterpretRequestDTO();
        }
        log.info("Interpret transit chart: userId={}, windowDays={}", userId, request.getWindowDays());
        AstrologyInterpretResponseDTO result = aiService.interpretTransit(userId, request);
        return Result.success(result);
    }
}

