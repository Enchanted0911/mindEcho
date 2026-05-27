package com.mindecho.module.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送消息请求
 */
@Data
public class SendMessageRequest {

    /** 会话ID（可选，为空则创建新会话） */
    private Long sessionId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过2000字")
    private String message;
}

