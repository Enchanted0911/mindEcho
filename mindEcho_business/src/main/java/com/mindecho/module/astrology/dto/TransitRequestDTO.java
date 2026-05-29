package com.mindecho.module.astrology.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 流运（过境）计算请求
 *
 * POST /api/astrology/transit
 */
@Data
public class TransitRequestDTO {

    /** 本命盘出生信息 */
    @Valid
    @NotNull(message = "出生信息不能为空")
    private BirthInfoDTO birthInfo;

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

