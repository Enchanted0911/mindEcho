package com.mindecho.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存流运目标日期请求 DTO
 *
 * <p>前端每次提交流运计算时，将选择的目标日期同步到 user 表，
 * 下次打开流运页面时可以自动回填。
 *
 * PUT /api/auth/profile/transit-date
 */
@Data
public class UpdateTransitDateRequest {

    /**
     * 流运目标日期，格式：yyyy-MM-dd
     * 如：2026-05-30
     */
    @NotBlank(message = "目标日期不能为空")
    private String targetDate;
}

