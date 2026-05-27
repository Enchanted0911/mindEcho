package com.mindecho.common.enums;

import lombok.Getter;

/**
 * 情绪类型枚举
 */
@Getter
public enum EmotionEnum {

    ANXIETY("anxiety", "焦虑"),
    DEPRESSION("depression", "抑郁"),
    ANGER("anger", "愤怒"),
    LONELINESS("loneliness", "孤独"),
    SADNESS("sadness", "悲伤"),
    HAPPINESS("happiness", "快乐"),
    FEAR("fear", "恐惧"),
    NEUTRAL("neutral", "平静"),
    STRESS("stress", "压力");

    private final String code;
    private final String desc;

    EmotionEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static EmotionEnum fromCode(String code) {
        for (EmotionEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return NEUTRAL;
    }
}

