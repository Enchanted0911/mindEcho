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
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 专用于 Web MVC HTTP 响应序列化的 ObjectMapper：
     * 1. Long/long → String，防止 JS Number 精度丢失（雪花ID超出 2^53-1）
     * 2. LocalDate/LocalDateTime → 可读字符串
     * 3. 忽略未知字段，保证兼容性
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
        javaTimeModule.addSerializer(LocalDate.class,
                new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_FORMAT)));
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATETIME_FORMAT)));
        javaTimeModule.addDeserializer(LocalDate.class,
                new LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE_FORMAT)));
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DATETIME_FORMAT)));
        mapper.registerModule(javaTimeModule);

        // 禁止将时间序列化为时间戳
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 忽略未知字段，防止第三方 API 新增字段导致反序列化失败
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return mapper;
    }
}

