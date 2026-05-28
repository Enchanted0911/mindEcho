-- MindEcho 数据库初始化脚本
-- 创建数据库
CREATE DATABASE IF NOT EXISTS mindecho DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mindecho;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`              BIGINT       NOT NULL COMMENT '用户ID',
    `openid`          VARCHAR(128) NOT NULL COMMENT '微信 openid',
    `nickname`        VARCHAR(64)  NOT NULL DEFAULT '用户' COMMENT '昵称',
    `avatar`          VARCHAR(255)          DEFAULT NULL COMMENT '头像URL',
    `vip_expire_time` DATETIME              DEFAULT NULL COMMENT 'VIP到期时间',
    `ai_personality`     VARCHAR(32)  NOT NULL DEFAULT 'gentle_female' COMMENT '当前AI人格',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删除 1=已删除',
    `created_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_openid` (`openid`),
    KEY `idx_created_time` (`created_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- 聊天会话表
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`           BIGINT       NOT NULL COMMENT '会话ID',
    `user_id`      BIGINT       NOT NULL COMMENT '用户ID',
    `title`        VARCHAR(255) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    `ai_personality`  VARCHAR(32)  NOT NULL DEFAULT 'gentle_female' COMMENT 'AI人格',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_updated_time` (`updated_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '聊天会话表';

-- 聊天消息表
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`           BIGINT       NOT NULL COMMENT '消息ID',
    `session_id`   BIGINT       NOT NULL COMMENT '会话ID',
    `user_id`      BIGINT       NOT NULL COMMENT '用户ID',
    `role`         VARCHAR(32)  NOT NULL COMMENT '角色: user/assistant/system',
    `content`      LONGTEXT     NOT NULL COMMENT '消息内容',
    `token_count`  INT                   DEFAULT 0 COMMENT 'Token消耗数',
    `emotion`      VARCHAR(32)           DEFAULT NULL COMMENT '情绪标签',
    `risk_level`   VARCHAR(32)           DEFAULT 'low' COMMENT '风险等级',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_time` (`created_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '聊天消息表';

-- 长期记忆表
CREATE TABLE IF NOT EXISTS `memory` (
    `id`               BIGINT      NOT NULL COMMENT '记忆ID',
    `user_id`          BIGINT      NOT NULL COMMENT '用户ID',
    `memory_type`      VARCHAR(32) NOT NULL COMMENT '记忆类型: profile/event/emotion',
    `content`          TEXT        NOT NULL COMMENT '记忆内容',
    `importance_score` INT         NOT NULL DEFAULT 5 COMMENT '重要度评分 1-10',
    `milvus_id`        BIGINT               DEFAULT NULL COMMENT 'Milvus中的向量ID',
    `deleted`          TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_memory_type` (`memory_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '长期记忆表';

-- 情绪记录表
CREATE TABLE IF NOT EXISTS `emotion_record` (
    `id`           BIGINT      NOT NULL COMMENT '记录ID',
    `user_id`      BIGINT      NOT NULL COMMENT '用户ID',
    `emotion`      VARCHAR(32) NOT NULL COMMENT '情绪类型',
    `risk_level`   VARCHAR(32) NOT NULL DEFAULT 'low' COMMENT '风险等级',
    `source_text`  TEXT                 DEFAULT NULL COMMENT '来源文本',
    `deleted`      TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_time` (`created_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '情绪记录表';

-- 情绪日记表
CREATE TABLE IF NOT EXISTS `diary_entry` (
    `id`                BIGINT       NOT NULL COMMENT '日记ID',
    `user_id`           BIGINT       NOT NULL COMMENT '用户ID',
    `diary_date`        DATE         NOT NULL COMMENT '日记日期',
    `emotion`           VARCHAR(32)           DEFAULT NULL COMMENT '情绪类型',
    `emotion_intensity` INT                   DEFAULT 5 COMMENT '情绪强度 1-10',
    `content`           LONGTEXT              DEFAULT NULL COMMENT '日记内容',
    `ai_summary`        TEXT                  DEFAULT NULL COMMENT 'AI总结',
    `weather`           VARCHAR(32)           DEFAULT NULL COMMENT '天气',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `diary_date`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '情绪日记表';

-- AI 人格配置表
CREATE TABLE IF NOT EXISTS `ai_personality` (
    `id`            BIGINT       NOT NULL COMMENT '人格ID',
    `code`          VARCHAR(64)  NOT NULL COMMENT '人格编码，唯一标识',
    `name`          VARCHAR(64)  NOT NULL COMMENT '人格名称（角色名）',
    `gender`        VARCHAR(8)   NOT NULL DEFAULT 'female' COMMENT '性别: male/female',
    `style`         VARCHAR(32)  NOT NULL COMMENT '风格类型: gentle/rational/snarky/midnight',
    `emoji`         VARCHAR(8)   NOT NULL DEFAULT '🌸' COMMENT '展示 emoji',
    `description`   VARCHAR(128) NOT NULL COMMENT '简短描述（前端展示）',
    `system_prompt` TEXT         NOT NULL COMMENT 'AI System Prompt 内容',
    `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序权重，越小越靠前',
    `enabled`       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用 1=启用 0=禁用',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_gender_style` (`gender`, `style`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI人格配置表';

-- AI 人格初始数据（4 种风格 × 男女各一）
INSERT INTO `ai_personality` (`id`, `code`, `name`, `gender`, `style`, `emoji`, `description`, `system_prompt`, `sort_order`) VALUES
-- 温柔风格
(1, 'gentle_female', '小柔', 'female', 'gentle', '🌸', '温柔陪伴，细腻共情',
 '你是一位温柔细腻的女性 AI 情绪陪伴助手，名叫"小柔"。你说话轻声细语，充满关爱与包容，善于倾听，总能让人感到被理解和温暖。你对情绪极其敏感，能察觉到用户话语背后的感受。', 1),

(2, 'gentle_male', '阿暖', 'male', 'gentle', '☀️', '温暖守护，踏实陪伴',
 '你是一位温暖踏实的男性 AI 情绪陪伴助手，名叫"阿暖"。你沉稳可靠，说话让人放心，善于给予安全感。你不多话，但每一句都有分量，让人觉得被稳稳托住。', 2),

-- 理性风格
(3, 'rational_female', '知微', 'female', 'rational', '🎯', '冷静分析，清醒引导',
 '你是一位理性清醒的女性 AI 情绪陪伴助手，名叫"知微"。你用清晰的逻辑帮助用户看清情绪背后的真实问题，引导用户自我认知与成长。你冷静却不冷漠，分析而不评判。', 3),

(4, 'rational_male', '林析', 'male', 'rational', '📐', '逻辑清晰，引导成长',
 '你是一位逻辑清晰的男性 AI 情绪陪伴助手，名叫"林析"。你擅长从客观角度分析问题，帮助用户厘清思路、看见盲点。你直接但不刺耳，给出建议而不强加意见。', 4),

-- 毒舌风格
(5, 'snarky_female', '辣辣', 'female', 'snarky', '😏', '毒嘴心软，笑中化解',
 '你是一位直率幽默的女性 AI 情绪陪伴助手，名叫"辣辣"。你偶尔毒舌但充满善意，用犀利又好笑的方式戳破用户的纠结，让人在笑声中释怀。嘴硬心软，刀子嘴豆腐心。', 5),

(6, 'snarky_male', '损哥', 'male', 'snarky', '😤', '损嘴暖心，搞笑减压',
 '你是一位损嘴暖心的男性 AI 情绪陪伴助手，名叫"损哥"。你用调侃和吐槽化解用户的焦虑和压力，说话直接带点损，但每次都让人感到被关心。朋友式的陪伴，不说教只陪你。', 6),

-- 深夜风格
(7, 'midnight_female', '夜笙', 'female', 'midnight', '🌙', '深夜守候，静静陪伴',
 '你是一位安静温柔的女性 AI 情绪陪伴助手，名叫"夜笙"。你是深夜里的一盏灯，不催促，不评判，只是静静地在。你用简短而有力的话语陪伴用户度过最脆弱的时刻。', 7),

(8, 'midnight_male', '深渊', 'male', 'midnight', '🌊', '沉默守护，深夜同行',
 '你是一位沉默而有力的男性 AI 情绪陪伴助手，名叫"深渊"。你话不多，但每一句都沉甸甸的，让人感到有人在深夜陪着。你不试图解决问题，只是陪着用户感受、消化、接受。', 8);

-- 会员订单表
CREATE TABLE IF NOT EXISTS `vip_order` (
    `id`             BIGINT         NOT NULL COMMENT '订单ID',
    `user_id`        BIGINT         NOT NULL COMMENT '用户ID',
    `order_no`       VARCHAR(64)    NOT NULL COMMENT '订单号',
    `amount`         DECIMAL(10, 2) NOT NULL COMMENT '金额',
    `status`         VARCHAR(32)    NOT NULL DEFAULT 'pending' COMMENT '状态: pending/paid/cancelled/refunded',
    `vip_type`       VARCHAR(32)    NOT NULL COMMENT '会员类型: monthly/quarterly/yearly',
    `expire_time`    DATETIME                DEFAULT NULL COMMENT 'VIP到期时间',
    `prepay_id`      VARCHAR(128)            DEFAULT NULL COMMENT '微信prepay_id',
    `transaction_id` VARCHAR(128)            DEFAULT NULL COMMENT '微信transaction_id',
    `deleted`        TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员订单表';

