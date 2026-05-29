package com.mindecho.module.astrology.controller;

import com.mindecho.common.result.Result;
import com.mindecho.common.util.UserContext;
import com.mindecho.module.astrology.dto.*;
import com.mindecho.module.astrology.service.AstrologyAiService;
import com.mindecho.module.astrology.service.AstrologyGatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 占星模块 REST 接口
 *
 * <p>接口清单（按 PRD §4）：
 * <pre>
 * POST /api/astrology/natal              单星盘计算（转发 Python）
 * POST /api/astrology/natal/interpret    单盘 AI 解读
 * POST /api/astrology/synastry           和盘计算（转发 Python）
 * POST /api/astrology/synastry/interpret 和盘 AI 解读
 * POST /api/astrology/transit            流运计算（转发 Python）
 * POST /api/astrology/transit/interpret  流运 AI 解读
 * GET  /api/astrology/natal/check        查询当前用户是否有缓存本命盘
 * DELETE /api/astrology/natal/cache      强制清除并刷新本命盘缓存
 * </pre>
 *
 * <p>认证：所有接口均需 JWT Token（由 {@code JwtInterceptor} 拦截验证）。
 */
@Slf4j
@RestController
@RequestMapping("/astrology")
@RequiredArgsConstructor
public class AstrologyController {

    private final AstrologyGatewayService gatewayService;
    private final AstrologyAiService aiService;

    // ─────────────────────── 单星盘（本命盘）─────────────────────────────

    /**
     * 计算本命盘（含缓存）
     *
     * <p>第一次调用转发 Python 计算，结果永久缓存到 Redis；
     * 后续调用直接返回缓存，无需重新计算。
     *
     * POST /api/astrology/natal
     */
    @PostMapping("/natal")
    public Result<NatalChartResponseDTO> calculateNatal(@Valid @RequestBody NatalRequestDTO request) {
        String userId = UserContext.getUserId();
        log.info("Calculate natal chart: userId={}, city={}", userId, request.getBirthInfo().getCity());
        NatalChartResponseDTO result = gatewayService.getNatalChart(userId, request);
        return Result.success(result);
    }

    /**
     * 单盘 AI 解读
     *
     * <p>请求体中的 {@code chart} 字段可直接使用 {@code /api/astrology/natal} 返回的 {@code chart} 字段，
     * 无需重新提交出生信息。
     *
     * POST /api/astrology/natal/interpret
     */
    @PostMapping("/natal/interpret")
    public Result<AstrologyInterpretResponseDTO> interpretNatal(
            @Valid @RequestBody AstrologyInterpretRequestDTO request) {
        String userId = UserContext.getUserId();
        log.info("Interpret natal chart: userId={}, focus={}", userId, request.getFocus());
        request.setInterpretType("NATAL");
        AstrologyInterpretResponseDTO result = aiService.interpretNatal(userId, request);
        return Result.success(result);
    }

    /**
     * 查询当前用户是否有缓存的本命盘
     *
     * GET /api/astrology/natal/check
     */
    @GetMapping("/natal/check")
    public Result<Boolean> checkNatalChart() {
        String userId = UserContext.getUserId();
        boolean hasChart = gatewayService.hasNatalChart(userId);
        return Result.success(hasChart);
    }

    /**
     * 强制刷新本命盘（清除缓存后重新计算）
     *
     * <p>适用场景：用户发现出生信息录入有误，需要重新计算。
     *
     * DELETE /api/astrology/natal/cache
     */
    @DeleteMapping("/natal/cache")
    public Result<NatalChartResponseDTO> refreshNatalChart(@Valid @RequestBody NatalRequestDTO request) {
        String userId = UserContext.getUserId();
        log.info("Refresh natal chart: userId={}", userId);
        NatalChartResponseDTO result = gatewayService.refreshNatalChart(userId, request);
        return Result.success("本命盘已重新计算", result);
    }

    // ─────────────────────── 和盘（合盘）────────────────────────────────

    /**
     * 计算和盘（含 30 天缓存）
     *
     * POST /api/astrology/synastry
     */
    @PostMapping("/synastry")
    public Result<SynastryResponseDTO> calculateSynastry(@Valid @RequestBody SynastryRequestDTO request) {
        String userId = UserContext.getUserId();
        log.info("Calculate synastry: userId={}, partner={}", userId, request.getPartnerName());
        SynastryResponseDTO result = gatewayService.getSynastryChart(userId, request);
        return Result.success(result);
    }

    /**
     * 和盘 AI 解读
     *
     * <p>请求体中 {@code chart} 使用 {@code /api/astrology/synastry} 返回的 {@code chart} 字段；
     * {@code extraContext} 格式：{@code "{partnerName}|{relationshipType}"}，如 {@code "小明|romantic"}
     *
     * POST /api/astrology/synastry/interpret
     */
    @PostMapping("/synastry/interpret")
    public Result<AstrologyInterpretResponseDTO> interpretSynastry(
            @Valid @RequestBody AstrologyInterpretRequestDTO request) {
        String userId = UserContext.getUserId();
        log.info("Interpret synastry chart: userId={}, focus={}", userId, request.getFocus());
        request.setInterpretType("SYNASTRY");
        AstrologyInterpretResponseDTO result = aiService.interpretSynastry(userId, request);
        return Result.success(result);
    }

    // ─────────────────────── 流运（过境）────────────────────────────────

    /**
     * 计算流运（含 24 小时缓存）
     *
     * POST /api/astrology/transit
     */
    @PostMapping("/transit")
    public Result<TransitResponseDTO> calculateTransit(@Valid @RequestBody TransitRequestDTO request) {
        String userId = UserContext.getUserId();
        log.info("Calculate transit: userId={}, targetDate={}", userId, request.getTargetDate());
        TransitResponseDTO result = gatewayService.getTransitChart(userId, request);
        return Result.success(result);
    }

    /**
     * 流运 AI 解读
     *
     * <p>请求体中 {@code chart} 使用 {@code /api/astrology/transit} 返回的 {@code chart} 字段；
     * {@code extraContext} 填入流运查询日期，如 {@code "2026-05-29"}
     *
     * POST /api/astrology/transit/interpret
     */
    @PostMapping("/transit/interpret")
    public Result<AstrologyInterpretResponseDTO> interpretTransit(
            @Valid @RequestBody AstrologyInterpretRequestDTO request) {
        String userId = UserContext.getUserId();
        log.info("Interpret transit chart: userId={}, focus={}", userId, request.getFocus());
        request.setInterpretType("TRANSIT");
        AstrologyInterpretResponseDTO result = aiService.interpretTransit(userId, request);
        return Result.success(result);
    }
}

