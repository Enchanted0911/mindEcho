package com.mindecho.module.diary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 情绪日记 DTO
 */
@Data
@Builder
public class DiaryEntryDTO {

    private Long id;
    private String diaryDate;
    private String emotion;
    private Integer emotionIntensity;
    private String content;
    private String aiSummary;
    private String weather;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;
}

