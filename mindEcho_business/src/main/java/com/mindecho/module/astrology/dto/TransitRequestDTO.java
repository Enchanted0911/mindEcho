package com.mindecho.module.astrology.dto;

import lombok.Data;

/**
 * 流运（过境）计算请求
 *
 * <p>出生信息由后端从 user 表读取，前端无需传出生信息。
 *
 * POST /api/astrology/transit
 */
@Data
public class TransitRequestDTO {

    /**
     * 查询日期（格式 yyyy-MM-dd），null = 今天
     * Python 服务以该日期作为过境时间点
     */
    private String targetDate;

    /**
     * 流运窗口天数（查询未来 N 天的重要流运事件），默认 30
     */
    private Integer windowDays = 30;
}

