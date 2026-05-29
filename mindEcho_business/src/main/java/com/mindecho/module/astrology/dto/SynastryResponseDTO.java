package com.mindecho.module.astrology.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 和盘（合盘）计算结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SynastryResponseDTO {

    /** 完整关系模型（含双方行星相位对照） */
    private JsonNode relationshipModel;

    /** 主要相位列表（如金星合月亮） */
    private List<JsonNode> aspects;

    /** 关系主题标签（如 "深度情感共鸣"、"价值观差异"） */
    private List<String> themes;

    /** 和盘原始完整数据（用于传入解读接口） */
    private JsonNode chart;
}

