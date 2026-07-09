package com.mindecho.module.astrology.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 流运（过境）计算结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransitResponseDTO {

    /** 查询日期（yyyy-MM-dd），来自 Python 响应的 date 字段 */
    private String date;

    /** 流运事件列表（每个事件含日期、行星、过境类型、影响描述） */
    private List<JsonNode> events;

    /** 流运摘要（最近 N 天最重要的流运主题） */
    private JsonNode summary;
}

