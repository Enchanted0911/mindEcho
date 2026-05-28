package com.mindecho.module.emotion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 情绪分析响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionAnalyzeResponse {

    /** 情绪类型 */
    private String emotion;

    /** 风险等级 */
    private String riskLevel;
}

