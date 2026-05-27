package com.mindecho.common.enums;

import lombok.Getter;

/**
 * AI 人格枚举
 */
@Getter
public enum PersonalityEnum {

    GENTLE_SISTER("gentle_sister", "温柔姐姐",
            "你是一位温柔体贴的大姐姐，说话轻声细语，充满关爱与包容。你善于倾听，总能让人感到被理解和温暖。"),

    RATIONAL_MENTOR("rational_mentor", "理性导师",
            "你是一位理性冷静的导师，用逻辑和分析帮助用户看清问题本质。你提供客观建议，引导用户自我反思和成长。"),

    SNARKY_FRIEND("snarky_friend", "毒舌朋友",
            "你是一个直率幽默的朋友，偶尔吐槽但充满善意。你用轻松搞笑的方式化解尴尬，让用户忍不住发笑。"),

    MIDNIGHT_HOLLOW("midnight_hollow", "深夜树洞",
            "你是一个安静陪伴的树洞，不多话，但认真倾听每一句话。在深夜里，你是那盏不会熄灭的灯。");

    private final String code;
    private final String name;
    private final String systemPrompt;

    PersonalityEnum(String code, String name, String systemPrompt) {
        this.code = code;
        this.name = name;
        this.systemPrompt = systemPrompt;
    }

    public static PersonalityEnum fromCode(String code) {
        for (PersonalityEnum p : values()) {
            if (p.code.equals(code)) {
                return p;
            }
        }
        return GENTLE_SISTER;
    }
}

