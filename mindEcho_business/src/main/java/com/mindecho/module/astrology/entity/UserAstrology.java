package com.mindecho.module.astrology.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 用户星盘信息表实体
 *
 * <p>将原 user 表中与占星相关的字段独立抽取到本表，与 user 表通过 user_id 关联（1:1）。
 *
 * <p>字段分组：
 * <ul>
 *   <li>出生信息：birth_city / birth_lat / birth_lng / birth_time</li>
 * <li>本命盘：natal_chart_data / natal_interpretation</li>
 *   <li>和盘：synastry_chart_data / synastry_interpretation</li>
 *   <li>和盘对方信息（前端回填用）：synastry_partner_name / city / lat / lng / time</li>
 *   <li>流运：transit_chart_data / transit_interpretation / transit_target_date</li>
 * </ul>
 */
@Data
@TableName("user_astrology")
public class UserAstrology {

    @TableId(type = IdType.AUTO)
    private UUID id;

    /** 关联 user 表的用户 ID（1:1 关系） */
    @TableField("user_id")
    private UUID userId;

    // ── 出生信息 ─────────────────────────────────────────────────────────────

    /** 出生城市名称 */
    @TableField("birth_city")
    private String birthCity;

    /** 出生地纬度 */
    @TableField("birth_lat")
    private Double birthLat;

    /** 出生地经度 */
    @TableField("birth_lng")
    private Double birthLng;

    /** 出生时间（格式：yyyy-MM-dd HH:mm，如 1995-08-15 14:30） */
    @TableField("birth_time")
    private String birthTime;

    // ── 本命盘 ───────────────────────────────────────────────────────────────

    /**
     * 本命盘原始星盘数据（JSON 格式，含 planets/angles/nodes/houses/aspects/metadata）
     *
     * <p>updateStrategy = IGNORED：允许 updateById 将该字段更新为 null（清空缓存场景需要）
     */
    @TableField(value = "natal_chart_data", updateStrategy = FieldStrategy.IGNORED)
    private String natalChartData;

    /**
     * 本命盘 AI 解读文本（最近一次，TEXT）
     *
     * <p>updateStrategy = IGNORED：允许 updateById 将该字段更新为 null（清空缓存场景需要）
     */
    @TableField(value = "natal_interpretation", updateStrategy = FieldStrategy.IGNORED)
    private String natalInterpretation;

    // ── 和盘 ─────────────────────────────────────────────────────────────────

    /**
     * 最近一次和盘原始数据（JSON 格式），按 partner_key 索引的 JSON Map
     *
     * <p>updateStrategy = IGNORED：允许 updateById 将该字段更新为 null（清空缓存场景需要）
     */
    @TableField(value = "synastry_chart_data", updateStrategy = FieldStrategy.IGNORED)
    private String synastryChartData;

    /**
     * 和盘 AI 解读文本（最近一次，TEXT）
     *
     * <p>updateStrategy = IGNORED：允许 updateById 将该字段更新为 null（清空缓存场景需要）
     */
    @TableField(value = "synastry_interpretation", updateStrategy = FieldStrategy.IGNORED)
    private String synastryInterpretation;

    // ── 和盘：最近一次对方出生信息（方便前端下次回填） ─────────────────────

    /** 最近一次和盘对方昵称 */
    @TableField("synastry_partner_name")
    private String synastryPartnerName;

    /** 最近一次和盘对方出生城市 */
    @TableField("synastry_partner_city")
    private String synastryPartnerCity;

    /** 最近一次和盘对方出生地纬度 */
    @TableField("synastry_partner_lat")
    private Double synastryPartnerLat;

    /** 最近一次和盘对方出生地经度 */
    @TableField("synastry_partner_lng")
    private Double synastryPartnerLng;

    /** 最近一次和盘对方出生时间（格式：yyyy-MM-dd HH:mm） */
    @TableField("synastry_partner_time")
    private String synastryPartnerTime;

    // ── 流运 ─────────────────────────────────────────────────────────────────

    /**
     * 流运原始星盘数据（JSON 格式，最近一次，含 date/chart/events/summary）
     *
     * <p>updateStrategy = IGNORED：允许 updateById 将该字段更新为 null（清空缓存场景需要）
     */
    @TableField(value = "transit_chart_data", updateStrategy = FieldStrategy.IGNORED)
    private String transitChartData;

    /**
     * 流运 AI 解读文本（最近一次，TEXT）
     *
     * <p>updateStrategy = IGNORED：允许 updateById 将该字段更新为 null（清空缓存场景需要）
     */
    @TableField(value = "transit_interpretation", updateStrategy = FieldStrategy.IGNORED)
    private String transitInterpretation;

    /**
     * 最近一次流运查询的目标日期（格式：yyyy-MM-dd）
     *
     * <p>updateStrategy = IGNORED：允许 updateById 将该字段更新为 null（清空缓存场景需要）
     */
    @TableField(value = "transit_target_date", updateStrategy = FieldStrategy.IGNORED)
    private String transitTargetDate;

    // ── 公共字段 ─────────────────────────────────────────────────────────────

    /** 逻辑删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间（带时区） */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private OffsetDateTime createdTime;

    /** 更新时间（带时区） */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedTime;
}

