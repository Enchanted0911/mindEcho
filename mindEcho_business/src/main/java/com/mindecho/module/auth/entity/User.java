package com.mindecho.module.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 用户实体
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 微信 openid */
    @TableField("openid")
    private String openid;

    /** 昵称 */
    @TableField("nickname")
    private String nickname;

    /** 头像 */
    @TableField("avatar")
    private String avatar;

    /** VIP 到期时间（带时区） */
    @TableField("vip_expire_time")
    private OffsetDateTime vipExpireTime;

    /** 当前使用的 AI 人格 */
    @TableField("ai_personality")
    private String aiPersonality;

    /** 出生城市名称 */
    @TableField("birth_city")
    private String birthCity;

    /** 出生地纬度 */
    @TableField("birth_lat")
    private Double birthLat;

    /** 出生地经度 */
    @TableField("birth_lng")
    private Double birthLng;

    /** 出生时间（格式：yyyy-MM-dd HH:mm，如 1995-08-15 14:30） */
    @TableField("birth_time")
    private String birthTime;

    /** 逻辑删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间（带时区） */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private OffsetDateTime createdTime;

    /** 更新时间（带时区） */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedTime;

    /**
     * 是否是 VIP（与服务器当前时区一致地比较）
     */
    public boolean isVip() {
        return vipExpireTime != null
                && vipExpireTime.isAfter(OffsetDateTime.now(ZoneId.of("Asia/Shanghai")));
    }
}

