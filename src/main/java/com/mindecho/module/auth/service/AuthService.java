package com.mindecho.module.auth.service;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import com.mindecho.common.util.JwtUtil;
import com.mindecho.module.auth.dto.LoginResponse;
import com.mindecho.module.auth.dto.WxLoginRequest;
import com.mindecho.module.auth.entity.User;
import com.mindecho.module.auth.mapper.UserMapper;
import com.mindecho.module.personality.service.PersonalityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PersonalityService personalityService;

    @Value("${wechat.appid}")
    private String appid;

    @Value("${wechat.secret}")
    private String secret;

    @Value("${wechat.login-url}")
    private String loginUrl;

    /**
     * 微信小程序登录
     */
    public LoginResponse wxLogin(WxLoginRequest request) {
        // 1. 调用微信 API 换取 openid
        String openid = getOpenid(request.getCode());

        // 2. 查询或创建用户
        User user = userMapper.findByOpenid(openid);
        if (user == null) {
            user = createUser(openid);
        }

        // 3. 生成 JWT Token
        String token = jwtUtil.generateToken(user.getId());

        // 4. 构建响应
        return buildLoginResponse(token, user);
    }

    /**
     * 调用微信接口获取 openid
     */
    private String getOpenid(String code) {
        String url = String.format("%s?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                loginUrl, appid, secret, code);

        try {
            String response = HttpUtil.get(url);
            JSONObject json = JSONUtil.parseObj(response);

            if (json.containsKey("errcode") && json.getInt("errcode") != 0) {
                log.error("WeChat login failed: {}", json);
                throw new BusinessException(ResultCode.WECHAT_LOGIN_FAILED);
            }

            return json.getStr("openid");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("WeChat login error", e);
            throw new BusinessException(ResultCode.WECHAT_LOGIN_FAILED);
        }
    }

    /**
     * 创建新用户
     */
    private User createUser(String openid) {
        User user = new User();
        user.setOpenid(openid);
        user.setNickname("用户" + openid.substring(openid.length() - 4));
        user.setAiPersonality(personalityService.getDefaultCode());
        userMapper.insert(user);
        log.info("New user created: openid={}", openid);
        return user;
    }

    /**
     * 构建登录响应
     */
    private LoginResponse buildLoginResponse(String token, User user) {
        LoginResponse.UserInfoDTO userInfo = LoginResponse.UserInfoDTO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .isVip(user.isVip())
                .aiPersonality(user.getAiPersonality())
                .vipExpireTime(user.getVipExpireTime() != null
                        ? user.getVipExpireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        : null)
                .build();

        return LoginResponse.builder()
                .token(token)
                .userInfo(userInfo)
                .build();
    }
}

