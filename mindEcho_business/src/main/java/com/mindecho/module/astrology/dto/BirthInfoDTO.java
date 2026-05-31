package com.mindecho.module.astrology.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 出生信息 DTO（单人，被多个请求复用）
 */
@Data
public class BirthInfoDTO {

    /** 出生年份，如 1995 */
    @NotNull(message = "出生年份不能为空")
    @Min(value = 1900, message = "出生年份不合法")
    @Max(value = 2100, message = "出生年份不合法")
    private Integer year;

    /** 出生月份 1-12 */
    @NotNull(message = "出生月份不能为空")
    @Min(value = 1, message = "月份必须在 1-12 之间")
    @Max(value = 12, message = "月份必须在 1-12 之间")
    private Integer month;

    /** 出生日期 1-31 */
    @NotNull(message = "出生日期不能为空")
    @Min(value = 1, message = "日期必须在 1-31 之间")
    @Max(value = 31, message = "日期必须在 1-31 之间")
    private Integer day;

    /** 出生小时 0-23 */
    @NotNull(message = "出生小时不能为空")
    @Min(value = 0, message = "小时必须在 0-23 之间")
    @Max(value = 23, message = "小时必须在 0-23 之间")
    private Integer hour;

    /** 出生分钟 0-59 */
    @NotNull(message = "出生分钟不能为空")
    @Min(value = 0, message = "分钟必须在 0-59 之间")
    @Max(value = 59, message = "分钟必须在 0-59 之间")
    private Integer minute;

    /** 出生城市名称（用于经纬度解析），如 "北京" */
    @NotBlank(message = "出生城市不能为空")
    private String city;

    /** 出生地纬度（可选，优先级高于 city 解析） */
    private Double latitude;

    /** 出生地经度（可选，优先级高于 city 解析） */
    private Double longitude;

    /** IANA 时区名称（如 Asia/Shanghai），可选，不传则由 Java 层根据经纬度推断 */
    private String timezone;
}

