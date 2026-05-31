package com.mindecho.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存和盘对方出生信息请求 DTO
 *
 * <p>前端每次提交和盘时，将对方信息同步到 user 表，
 * 下次打开和盘页面时可以自动回填。
 *
 * PUT /api/auth/profile/synastry-partner
 */
@Data
public class UpdateSynastryPartnerRequest {

    /** 对方昵称（可选） */
    private String partnerName;

    /** 对方出生城市名称（必填） */
    @NotBlank(message = "对方出生城市不能为空")
    private String partnerCity;

    /** 对方出生地纬度 */
    private Double partnerLat;

    /** 对方出生地经度 */
    private Double partnerLng;

    /**
     * 对方出生时间，格式：yyyy-MM-dd HH:mm
     * 如：1997-03-22 10:00
     */
    @NotBlank(message = "对方出生时间不能为空")
    private String partnerTime;
}

