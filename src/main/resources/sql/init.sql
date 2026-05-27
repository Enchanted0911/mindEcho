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
    `personality`     VARCHAR(32)  NOT NULL DEFAULT 'gentle_sister' COMMENT '当前AI人格',
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
    `personality`  VARCHAR(32)  NOT NULL DEFAULT 'gentle_sister' COMMENT 'AI人格',
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

