package com.mindecho.common.enums;

import lombok.Getter;

/**
 * 风险等级枚举
 */
@Getter
public enum RiskLevelEnum {

    LOW("low", "普通情绪"),
    MEDIUM("medium", "明显低落"),
    HIGH("high", "高风险");

    private final String code;
    private final String desc;

    RiskLevelEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static RiskLevelEnum fromCode(String code) {
        for (RiskLevelEnum r : values()) {
            if (r.code.equals(code)) {
                return r;
            }
        }
        return LOW;
    }

    public boolean isHigh() {
        return this == HIGH;
    }
}

