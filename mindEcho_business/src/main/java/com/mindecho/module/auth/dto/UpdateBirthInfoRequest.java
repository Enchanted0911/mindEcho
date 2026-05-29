package com.mindecho.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新出生信息请求 DTO
 */
@Data
public class UpdateBirthInfoRequest {

    /** 出生城市名称（必填） */
    @NotBlank(message = "出生城市不能为空")
    private String birthCity;

    /** 出生地纬度 */
    private Double birthLat;

    /** 出生地经度 */
    private Double birthLng;

    /**
     * 出生时间，格式：yyyy-MM-dd HH:mm
     * 如：1995-08-15 14:30
     */
    @NotBlank(message = "出生时间不能为空")
    private String birthTime;
}

