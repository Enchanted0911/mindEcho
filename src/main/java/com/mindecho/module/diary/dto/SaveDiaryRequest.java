package com.mindecho.module.diary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

/**
 * 保存日记请求
 */
@Data
public class SaveDiaryRequest {

    /** 日记日期（不填则为今天） */
    private LocalDate diaryDate;

    /** 情绪类型 */
    private String emotion;

    @Min(value = 1, message = "情绪强度最小1")
    @Max(value = 10, message = "情绪强度最大10")
    private Integer emotionIntensity;

    /** 日记内容 */
    private String content;

    /** 天气 */
    private String weather;
}

