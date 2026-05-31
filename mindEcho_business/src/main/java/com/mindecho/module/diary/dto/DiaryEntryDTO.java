package com.mindecho.module.diary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 情绪日记 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaryEntryDTO {

    private UUID id;
    private String diaryDate;
    private String emotion;
    private Integer emotionIntensity;
    private String content;
    private String aiSummary;
    private String weather;
    /** ISO-8601 带时区格式 */
    private OffsetDateTime createdTime;
    /** ISO-8601 带时区格式 */
    private OffsetDateTime updatedTime;
}

