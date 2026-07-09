package com.mindecho.module.astrology.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 用户星盘信息汇总 DTO（供登录/首屏一次性下发）
 *
 * <p>登录成功后前端可通过 {@code GET /api/astrology/info} 拉取该 DTO，
 * 缓存到本地 store，从而避免进入各星盘页时重复弹出设置表单。
 *
 * <p>注意：natalChartData / synastryChartData / transitChartData 体积较大，
 * 本 DTO 仅返回"是否存在缓存"的标志位（hasNatalCache 等），
 * 实际 chart JSON 仍通过各功能接口按需下发。
 */
@Data
@Builder
public class UserAstrologyInfoDTO {

    // ── 出生信息 ─────────────────────────────────────────────────────────────

    /** 出生城市名称（null 表示未设置） */
    private String birthCity;

    /** 出生地纬度 */
    private Double birthLat;

    /** 出生地经度 */
    private Double birthLng;

    /** 出生时间（格式 yyyy-MM-dd HH:mm，null 表示未设置） */
    private String birthTime;

    // ── 缓存标志位 ───────────────────────────────────────────────────────────

    /** 是否存在本命盘缓存（DB 或 Redis） */
    private boolean hasNatalCache;

    /** 是否存在和盘缓存 */
    private boolean hasSynastryCache;

    /** 是否存在流运缓存 */
    private boolean hasTransitCache;

    // ── 和盘：最近一次对方出生信息（前端回填用） ─────────────────────────────

    /** 最近一次和盘对方昵称 */
    private String synastryPartnerName;

    /** 最近一次和盘对方出生城市 */
    private String synastryPartnerCity;

    /** 最近一次和盘对方出生地纬度 */
    private Double synastryPartnerLat;

    /** 最近一次和盘对方出生地经度 */
    private Double synastryPartnerLng;

    /** 最近一次和盘对方出生时间（格式 yyyy-MM-dd HH:mm） */
    private String synastryPartnerTime;

    // ── 流运：最近一次查询的目标日期 ──────────────────────────────────────────

    /** 最近一次流运查询的目标日期（格式 yyyy-MM-dd） */
    private String transitTargetDate;
}

