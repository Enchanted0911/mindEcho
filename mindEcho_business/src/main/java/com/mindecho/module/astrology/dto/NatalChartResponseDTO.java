package com.mindecho.module.astrology.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单星盘（本命盘）计算结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NatalChartResponseDTO {

    /**
     * 完整星盘原始数据（来自 Python，含所有行星位置、宫位、相位）
     * 前端可直接渲染或存档，解读接口复用此字段
     */
    private JsonNode chart;

    /**
     * 星盘摘要（Python 服务提取的结构化摘要，如太阳/月亮/上升星座）
     * 用于前端快速展示，无需解析完整 chart
     */
    private JsonNode summary;

    /** 是否已保存到用户档案 */
    private boolean savedToProfile;
}

