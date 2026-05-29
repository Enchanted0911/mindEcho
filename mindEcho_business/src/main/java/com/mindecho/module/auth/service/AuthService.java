package com.mindecho.module.auth.service;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mindecho.common.config.WechatMiniConfig;
import com.mindecho.common.exception.BusinessException;
import com.mindecho.common.result.ResultCode;
import com.mindecho.common.util.JwtUtil;
import com.mindecho.module.auth.dto.LoginResponse;
import com.mindecho.module.auth.dto.UpdateBirthInfoRequest;
import com.mindecho.module.auth.dto.WxLoginRequest;
import com.mindecho.module.auth.entity.User;
import com.mindecho.module.auth.mapper.UserMapper;
import com.mindecho.module.billing.service.PointAccountService;
import com.mindecho.module.personality.service.PersonalityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String WECHAT_ERRCODE_KEY = "errcode";
    private static final String WECHAT_OPENID_KEY  = "openid";

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PersonalityService personalityService;
    private final PointAccountService pointAccountService;
    private final WechatMiniConfig wechatConfig;

    /**
     * 微信小程序登录
     *
     * <p>包含用户首次注册的初始化逻辑，整体声明为事务，确保
     * 新用户记录与积分账户创建的原子性。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse wxLogin(WxLoginRequest request) {
        String openid = getOpenid(request.getCode());

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getOpenid, openid)
                        .eq(User::getDeleted, 0)
        );
        if (user == null) {
            user = initNewUser(openid);
        }

        String token = jwtUtil.generateToken(user.getId());
        return buildLoginResponse(token, user);
    }

    /**
     * 获取当前用户信息
     */
    public LoginResponse.UserInfoDTO getProfile(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return buildUserInfoDTO(user);
    }

    /**
     * 更新用户出生信息
     */
    public LoginResponse.UserInfoDTO updateBirthInfo(String userId, UpdateBirthInfoRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        user.setBirthCity(request.getBirthCity());
        user.setBirthLat(request.getBirthLat());
        user.setBirthLng(request.getBirthLng());
        user.setBirthTime(request.getBirthTime());
        userMapper.updateById(user);
        log.info("Birth info updated for userId={}, city={}", userId, request.getBirthCity());
        return buildUserInfoDTO(user);
    }

    // ─── 私有方法 ─────────────────────────────────────────────────────────────

    /**
     * 初始化新用户记录和积分账户（在事务方法内调用，事务由调用方保证）
     */
    private User initNewUser(String openid) {
        User user = new User();
        user.setOpenid(openid);
        user.setNickname("用户" + openid.substring(openid.length() - 4));
        user.setAiPersonality(personalityService.getDefaultCode());
        userMapper.insert(user);
        log.info("New user created: openid={}", openid);
        pointAccountService.getOrCreateAccount(user.getId());
        return user;
    }

    private String getOpenid(String code) {
        String url = String.format("%s?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                wechatConfig.getLoginUrl(), wechatConfig.getAppid(), wechatConfig.getSecret(), code);
        try {
            String response = HttpUtil.get(url);
            JSONObject json = JSONUtil.parseObj(response);
            if (json.containsKey(WECHAT_ERRCODE_KEY) && json.getInt(WECHAT_ERRCODE_KEY) != 0) {
                log.error("WeChat login failed: {}", json);
                throw new BusinessException(ResultCode.WECHAT_LOGIN_FAILED);
            }
            return json.getStr(WECHAT_OPENID_KEY);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("WeChat login error", e);
            throw new BusinessException(ResultCode.WECHAT_LOGIN_FAILED);
        }
    }

    private LoginResponse buildLoginResponse(String token, User user) {
        return LoginResponse.builder()
                .token(token)
                .userInfo(buildUserInfoDTO(user))
                .build();
    }

    private LoginResponse.UserInfoDTO buildUserInfoDTO(User user) {
        return LoginResponse.UserInfoDTO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .isVip(user.isVip())
                .aiPersonality(user.getAiPersonality())
                .vipExpireTime(user.getVipExpireTime() != null
                        ? user.getVipExpireTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        : null)
                .birthCity(user.getBirthCity())
                .birthLat(user.getBirthLat())
                .birthLng(user.getBirthLng())
                .birthTime(user.getBirthTime())
                .build();
    }
}

