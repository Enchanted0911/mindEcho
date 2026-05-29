package com.mindecho.module.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 聊天会话 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionDTO {

    private String id;
    private String title;
    private String aiPersonality;
    /** ISO-8601 带时区格式 */
    private OffsetDateTime createdTime;
    /** ISO-8601 带时区格式 */
    private OffsetDateTime updatedTime;
}

