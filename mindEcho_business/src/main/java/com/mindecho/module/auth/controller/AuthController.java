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
     * 更新用户出生信息（需要登录）
     * PUT /api/auth/profile/birth
     */
    @PutMapping("/profile/birth")
    public Result<LoginResponse.UserInfoDTO> updateBirthInfo(@Valid @RequestBody UpdateBirthInfoRequest request) {
        Long userId = UserContext.getUserId();
        log.info("UpdateBirthInfo: userId={}, city={}", userId, request.getBirthCity());
        LoginResponse.UserInfoDTO userInfo = authService.updateBirthInfo(userId, request);
        return Result.success(userInfo);
    }
}

