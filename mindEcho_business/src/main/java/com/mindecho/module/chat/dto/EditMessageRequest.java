package com.mindecho.module.chat.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.IOException;

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
    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long messageId;

    /**
     * 会话ID
     */
    @NotNull(message = "会话ID不能为空")
    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long sessionId;

    /**
     * 编辑后的消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过2000字")
    private String message;

    /**
     * 支持 JSON 数字和字符串两种格式的 Long 反序列化器
     */
    static class FlexibleLongDeserializer extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            String text = p.getText();
            if (text == null || text.isBlank() || "null".equals(text)) {
                return null;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return p.getLongValue();
            }
        }

        @Override
        public Long getNullValue(DeserializationContext ctx) {
            return null;
        }
    }
}

