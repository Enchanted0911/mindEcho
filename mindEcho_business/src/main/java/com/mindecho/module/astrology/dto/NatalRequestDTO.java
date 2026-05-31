package com.mindecho.module.astrology.dto;

import lombok.Data;

/**
 * 单星盘（本命盘）计算请求
 *
 * <p>本命盘计算只需传 userId（从 JWT 中获取），
 * 出生信息由后端从 user 表读取，前端无需再传出生信息。
 *
 * POST /api/astrology/natal
 */
@Data
public class NatalRequestDTO {
    // 请求体可为空，userId 从 JWT 上下文中获取
}

