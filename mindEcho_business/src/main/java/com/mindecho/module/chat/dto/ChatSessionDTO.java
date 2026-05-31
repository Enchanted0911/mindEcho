package com.mindecho.module.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 聊天会话 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionDTO {

    private UUID id;
    private String title;
    private String aiPersonality;
    /** ISO-8601 带时区格式 */
    private OffsetDateTime createdTime;
    /** ISO-8601 带时区格式 */
    private OffsetDateTime updatedTime;
}

