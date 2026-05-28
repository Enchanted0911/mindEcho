package com.mindecho.module.emotion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 情绪分析请求
 */
@Data
public class EmotionAnalyzeRequest {

    @NotBlank(message = "文本不能为空")
    @Size(max = 1000, message = "文本不能超过1000字")
    private String text;
}

