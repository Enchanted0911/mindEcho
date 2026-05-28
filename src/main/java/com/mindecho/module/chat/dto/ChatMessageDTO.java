package com.mindecho.module.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息 DTO
 */
@Data
@Builder
public class ChatMessageDTO {

    private Long id;
    private String role;
    private String content;
    private String emotion;
    private String riskLevel;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;
}

