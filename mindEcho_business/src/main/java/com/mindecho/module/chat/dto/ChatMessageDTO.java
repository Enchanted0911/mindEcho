package com.mindecho.module.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 聊天消息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {

    private String id;
    private String role;
    private String content;
    private String emotion;
    private String riskLevel;
    /** ISO-8601 带时区格式，如 2026-05-30T10:30:00+08:00 */
    private OffsetDateTime createdTime;
}

