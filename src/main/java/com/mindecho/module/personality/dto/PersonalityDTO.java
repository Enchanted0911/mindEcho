package com.mindecho.module.personality.dto;

import lombok.Builder;
import lombok.Data;

/**
 * AI 人格 DTO（前端展示用）
 */
@Data
@Builder
public class PersonalityDTO {

    /** 人格编码 */
    private String code;

    /** 角色名 */
    private String name;

    /** 性别：female / male */
    private String gender;

    /** 风格类型：gentle / rational / snarky / midnight */
    private String style;

    /** 展示 emoji */
    private String emoji;

    /** 简短描述 */
    private String description;
}

