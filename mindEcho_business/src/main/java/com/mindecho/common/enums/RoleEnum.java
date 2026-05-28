package com.mindecho.common.enums;

import lombok.Getter;

/**
 * 消息角色枚举
 */
@Getter
public enum RoleEnum {

    USER("user", "用户"),
    ASSISTANT("assistant", "AI助手"),
    SYSTEM("system", "系统");

    private final String code;
    private final String desc;

    RoleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

