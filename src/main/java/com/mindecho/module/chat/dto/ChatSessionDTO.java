package com.mindecho.module.chat.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天会话 DTO
 */
@Data
@Builder
public class ChatSessionDTO {

    private Long id;
    private String title;
    private String personality;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

