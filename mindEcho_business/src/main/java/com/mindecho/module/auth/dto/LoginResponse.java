package com.mindecho.module.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

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
        private UUID id;
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

        // ── 和盘：最近一次对方出生信息（前端回填用） ────────────────────
        /** 最近一次和盘对方昵称 */
        private String synastryPartnerName;
        /** 最近一次和盘对方出生城市 */
        private String synastryPartnerCity;
        /** 最近一次和盘对方出生地纬度 */
        private Double synastryPartnerLat;
        /** 最近一次和盘对方出生地经度 */
        private Double synastryPartnerLng;
        /** 最近一次和盘对方出生时间 yyyy-MM-dd HH:mm */
        private String synastryPartnerTime;

        // ── 流运：最近一次查询的目标日期 ────────────────────────────────
        /** 最近一次流运查询的目标日期 yyyy-MM-dd */
        private String transitTargetDate;
    }
}

