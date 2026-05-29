package com.mindecho.module.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** JWT Token */
    private String token;

    /** 用户信息 */
    private UserInfoDTO userInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfoDTO {
        private Long id;
        private String nickname;
        private String avatar;
        /**
         * Jackson 对 Boolean 类型的 isXxx 字段会将序列化属性名自动改为 xxx（去掉 is 前缀）。
         * 使用 @JsonProperty("isVip") 强制输出字段名为 isVip，与前端 UserInfo 接口保持一致。
         */
        @JsonProperty("isVip")
        private Boolean isVip;
        private String vipExpireTime;
        private String aiPersonality;
        /** 出生城市 */
        private String birthCity;
        /** 出生地纬度 */
        private Double birthLat;
        /** 出生地经度 */
        private Double birthLng;
        /** 出生时间 yyyy-MM-dd HH:mm */
        private String birthTime;
    }
}

