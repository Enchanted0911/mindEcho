package com.mindecho.module.emotion.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 情绪分析响应
 */
@Data
@Builder
public class EmotionAnalyzeResponse {

    /** 情绪类型 */
    private String emotion;

    /** 风险等级 */
    private String riskLevel;
}

