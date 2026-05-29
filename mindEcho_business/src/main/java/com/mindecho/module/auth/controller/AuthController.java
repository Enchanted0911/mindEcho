package com.mindecho.module.auth.controller;

import com.mindecho.common.result.Result;
import com.mindecho.common.util.UserContext;
import com.mindecho.module.auth.dto.LoginResponse;
import com.mindecho.module.auth.dto.UpdateBirthInfoRequest;
import com.mindecho.module.auth.dto.WxLoginRequest;
import com.mindecho.module.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证 Controller
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 微信小程序登录
     * POST /api/auth/wx-login
     */
    @PostMapping("/wx-login")
    public Result<LoginResponse> wxLogin(@Valid @RequestBody WxLoginRequest request) {
        log.info("WxLogin request: code={}", request.getCode());
        LoginResponse response = authService.wxLogin(request);
        return Result.success(response);
    }

    /**
     * 获取当前登录用户信息（需要登录）
     * GET /api/auth/profile
     * 前端可在 VIP 页面、个人中心等处调用，拉取最新 VIP 状态
     */
    @GetMapping("/profile")
    public Result<LoginResponse.UserInfoDTO> getProfile() {
        String userId = UserContext.getUserId();
        LoginResponse.UserInfoDTO userInfo = authService.getProfile(userId);
        return Result.success(userInfo);
    }

    /**
     * 更新用户出生信息（需要登录）
     * PUT /api/auth/profile/birth
     */
    @PutMapping("/profile/birth")
    public Result<LoginResponse.UserInfoDTO> updateBirthInfo(@Valid @RequestBody UpdateBirthInfoRequest request) {
        String userId = UserContext.getUserId();
        log.info("UpdateBirthInfo: userId={}, city={}", userId, request.getBirthCity());
        LoginResponse.UserInfoDTO userInfo = authService.updateBirthInfo(userId, request);
        return Result.success(userInfo);
    }
}

