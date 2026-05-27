package com.mindecho.module.auth.controller;

import com.mindecho.common.result.Result;
import com.mindecho.module.auth.dto.LoginResponse;
import com.mindecho.module.auth.dto.WxLoginRequest;
import com.mindecho.module.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}

