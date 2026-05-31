package com.mindecho.module.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * 编辑消息请求
 * 编辑后将删除该消息之后的所有消息，并以新内容重新发送
 */
@Data
public class EditMessageRequest {

    /**
     * 要编辑的消息ID
     */
    @NotNull(message = "消息ID不能为空")
    private UUID messageId;

    /**
     * 会话ID
     */
    @NotNull(message = "会话ID不能为空")
    private UUID sessionId;

    /**
     * 编辑后的消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过2000字")
    private String message;
}

