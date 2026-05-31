package com.mindecho.module.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 用户实体
 *
 * <p>注意：出生信息与星盘数据已迁移至 user_astrology 表，通过 userId 关联。
 */
@Data
@TableName("\"user\"")
public class User {

    @TableId(type = IdType.AUTO)
    private UUID id;

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

