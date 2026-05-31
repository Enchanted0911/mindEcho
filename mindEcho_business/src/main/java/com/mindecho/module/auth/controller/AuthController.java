package com.mindecho.module.auth.controller;

import com.mindecho.common.result.Result;
import com.mindecho.common.util.UserContext;
import com.mindecho.module.auth.dto.*;
import com.mindecho.module.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
        UUID userId = UserContext.getUserId();
        LoginResponse.UserInfoDTO userInfo = authService.getProfile(userId);
        return Result.success(userInfo);
    }

    /**
     * 更新用户出生信息（需要登录）
     * PUT /api/auth/profile/birth
     */
    @PutMapping("/profile/birth")
    public Result<LoginResponse.UserInfoDTO> updateBirthInfo(@Valid @RequestBody UpdateBirthInfoRequest request) {
        UUID userId = UserContext.getUserId();
        log.info("UpdateBirthInfo: userId={}, city={}", userId, request.getBirthCity());
        LoginResponse.UserInfoDTO userInfo = authService.updateBirthInfo(userId, request);
        return Result.success(userInfo);
    }

    /**
     * 保存和盘对方出生信息（需要登录）
     *
     * <p>前端每次提交和盘计算时调用，将对方信息持久化到 user 表。
     * 下次打开和盘页面时，前端从 /api/auth/profile 接口获取后自动回填。
     *
     * PUT /api/auth/profile/synastry-partner
     */
    @PutMapping("/profile/synastry-partner")
    public Result<LoginResponse.UserInfoDTO> updateSynastryPartner(
            @Valid @RequestBody UpdateSynastryPartnerRequest request) {
        UUID userId = UserContext.getUserId();
        log.info("UpdateSynastryPartner: userId={}, partnerCity={}", userId, request.getPartnerCity());
        LoginResponse.UserInfoDTO userInfo = authService.updateSynastryPartner(userId, request);
        return Result.success(userInfo);
    }

    /**
     * 保存流运目标日期（需要登录）
     *
     * <p>前端每次提交流运计算时调用，将目标日期持久化到 user 表。
     * 下次打开流运页面时，前端从 /api/auth/profile 接口获取后自动回填。
     *
     * PUT /api/auth/profile/transit-date
     */
    @PutMapping("/profile/transit-date")
    public Result<LoginResponse.UserInfoDTO> updateTransitDate(
            @Valid @RequestBody UpdateTransitDateRequest request) {
        UUID userId = UserContext.getUserId();
        log.info("UpdateTransitDate: userId={}, date={}", userId, request.getTargetDate());
        LoginResponse.UserInfoDTO userInfo = authService.updateTransitDate(userId, request);
        return Result.success(userInfo);
    }
}

