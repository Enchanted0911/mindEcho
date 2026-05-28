package com.mindecho.module.diary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
    @Size(max = 32, message = "情绪类型最多32个字符")
    private String emotion;

    @Min(value = 1, message = "情绪强度最小1")
    @Max(value = 10, message = "情绪强度最大10")
    private Integer emotionIntensity;

    /** 日记内容（限制最大长度，防止 AI token 溢出） */
    @Size(max = 5000, message = "日记内容最多5000个字符")
    private String content;

    /** 天气 */
    @Size(max = 32, message = "天气最多32个字符")
    private String weather;
}

