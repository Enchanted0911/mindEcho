package com.mindecho.module.chat.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.IOException;

/**
 * 发送消息请求
 */
@Data
public class SendMessageRequest {

    /**
     * 会话ID（可选，为空则创建新会话）
     * 前端使用字符串存储 Long ID（防止 JS 精度丢失），Jackson 默认不支持从 JSON 字符串
     * 反序列化为 Long，需要自定义反序列化器同时支持 JSON 数字和字符串格式。
     */
    @JsonDeserialize(using = FlexibleLongDeserializer.class)
    private Long sessionId;

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

