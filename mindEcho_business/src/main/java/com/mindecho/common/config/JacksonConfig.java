package com.mindecho.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.OffsetDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 配置（独立类，避免与 WebMvcConfig 循环依赖）
 *
 * <p>注意：不使用 @Primary，避免覆盖 Spring AI 内部使用的 ObjectMapper。
 * Spring AI 的 RestClient 会注入 Primary ObjectMapper 来反序列化 OpenAI/DeepSeek 响应，
 * 若被替换为我们的版本（未配置 FAIL_ON_UNKNOWN_PROPERTIES=false），
 * DeepSeek 返回新字段（如 prompt_tokens_details）时会抛 UnrecognizedPropertyException。
 *
 * <p>解决方案：用 @Qualifier("webObjectMapper") 命名隔离，仅注入到 MVC 层的 HttpMessageConverter。
 *
 * <p>时间格式策略：
 * <ul>
 *   <li>{@link java.time.OffsetDateTime} → ISO-8601 带时区格式（{@code 2026-05-30T10:30:00+08:00}），
 *       前端可直接解析，跨时区不歧义</li>
 *   <li>{@link LocalDate} → {@code yyyy-MM-dd}（日记日期等纯日期场景）</li>
 *   <li>{@link LocalDateTime} → {@code yyyy-MM-dd HH:mm:ss}（兼容旧接口入参反序列化）</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 专用于 Web MVC HTTP 响应序列化的 ObjectMapper：
     * 1. Long/long → String，防止 JS Number 精度丢失（雪花ID超出 2^53-1）
     * 2. OffsetDateTime → ISO-8601 带时区字符串（2026-05-30T10:30:00+08:00）
     * 3. LocalDate/LocalDateTime → 可读字符串（兼容旧入参）
     * 4. 忽略未知字段，保证兼容性
     */
    @Bean
    public ObjectMapper webObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Long -> String（解决雪花ID精度丢失）
        SimpleModule longModule = new SimpleModule();
        longModule.addSerializer(Long.class, ToStringSerializer.instance);
        longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        mapper.registerModule(longModule);

        // Java8 时间类型序列化
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // OffsetDateTime：使用标准 ISO-8601，输出如 2026-05-30T10:30:00+08:00
        // JavaTimeModule 默认支持 OffsetDateTime 反序列化，此处仅注册 Serializer 确保格式正确
        javaTimeModule.addSerializer(java.time.OffsetDateTime.class,
                OffsetDateTimeSerializer.INSTANCE);

        // LocalDate：纯日期格式
        javaTimeModule.addSerializer(LocalDate.class,
                new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_FORMAT)));
        javaTimeModule.addDeserializer(LocalDate.class,
                new LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE_FORMAT)));

        // LocalDateTime：兼容旧接口入参（如用户手动传入时间字符串）
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATETIME_FORMAT)));
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DATETIME_FORMAT)));

        mapper.registerModule(javaTimeModule);

        // 禁止将时间序列化为时间戳数字
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 忽略未知字段，防止第三方 API 新增字段导致反序列化失败
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return mapper;
    }
}

