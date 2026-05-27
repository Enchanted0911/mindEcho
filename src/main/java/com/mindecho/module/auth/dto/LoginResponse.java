package com.mindecho.module.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应 DTO
 */
@Data
@Builder
public class LoginResponse {

    /** JWT Token */
    private String token;

    /** 用户信息 */
    private UserInfoDTO userInfo;

    @Data
    @Builder
    public static class UserInfoDTO {
        private Long id;
        private String nickname;
        private String avatar;
        private Boolean isVip;
        private String vipExpireTime;
        private String personality;
    }
}

