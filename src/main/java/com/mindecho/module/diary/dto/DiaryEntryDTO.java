package com.mindecho.module.diary.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 情绪日记 DTO
 */
@Data
@Builder
public class DiaryEntryDTO {

    private Long id;
    private LocalDate diaryDate;
    private String emotion;
    private Integer emotionIntensity;
    private String content;
    private String aiSummary;
    private String weather;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

