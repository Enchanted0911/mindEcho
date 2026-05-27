package com.mindecho.module.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 微信 openid */
    @TableField("openid")
    private String openid;

    /** 昵称 */
    @TableField("nickname")
    private String nickname;

    /** 头像 */
    @TableField("avatar")
    private String avatar;

    /** VIP 到期时间 */
    @TableField("vip_expire_time")
    private LocalDateTime vipExpireTime;

    /** 当前使用的 AI 人格 */
    @TableField("personality")
    private String personality;

    /** 逻辑删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /** 创建时间 */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /**
     * 是否是 VIP
     */
    public boolean isVip() {
        return vipExpireTime != null && vipExpireTime.isAfter(LocalDateTime.now());
    }
}

