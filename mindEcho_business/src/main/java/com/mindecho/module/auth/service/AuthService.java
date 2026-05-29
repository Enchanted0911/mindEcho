package com.mindecho.module.auth.service;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.beans.factory.annotation.Value;
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

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PersonalityService personalityService;
    private final PointAccountService pointAccountService;

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
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getOpenid, openid)
                        .eq(User::getDeleted, 0)
        );
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
     * 创建新用户（事务保证用户记录与积分账户一起创建成功，否则全部回滚）
     */
    @Transactional(rollbackFor = Exception.class)
    protected User createUser(String openid) {
        User user = new User();
        user.setOpenid(openid);
        user.setNickname("用户" + openid.substring(openid.length() - 4));
        user.setAiPersonality(personalityService.getDefaultCode());
        userMapper.insert(user);
        log.info("New user created: openid={}", openid);

        // 初始化积分账户（赠送新用户礼包积分）
        // 不捕获异常，让事务回滚保证用户记录与积分账户的原子性
        pointAccountService.getOrCreateAccount(user.getId());

        return user;
    }

    /**
     * 更新用户出生信息
     *
     * @param userId  当前用户 ID
     * @param request 出生信息请求
     * @return 更新后的用户信息
     */
    public LoginResponse.UserInfoDTO updateBirthInfo(Long userId, UpdateBirthInfoRequest request) {
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

    /**
     * 构建登录响应
     */
    private LoginResponse buildLoginResponse(String token, User user) {
        return LoginResponse.builder()
                .token(token)
                .userInfo(buildUserInfoDTO(user))
                .build();
    }

    /**
     * 将 User 实体转换为 UserInfoDTO（供登录响应和更新响应共用）
     */
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

