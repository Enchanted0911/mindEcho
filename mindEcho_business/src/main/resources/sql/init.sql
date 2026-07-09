-- MindEcho 数据库初始化脚本（PostgreSQL + pgvector）
-- 前提：数据库 mindecho 已创建，pgvector 扩展已安装
-- psql -U postgres -c "CREATE DATABASE mindecho;"
-- psql -U postgres -d mindecho -c "CREATE EXTENSION IF NOT EXISTS vector;"
--
-- 时区策略：所有时间戳字段使用 TIMESTAMPTZ（带时区），存储为 UTC，读取时按客户端 time_zone 转换。
-- Java 层对应 java.time.OffsetDateTime（+08:00 Asia/Shanghai），前端收到 ISO-8601 带时区格式。
--
-- ID 策略：所有业务表主键使用 UUID（gen_random_uuid() 自动生成），Java 层使用 MyBatis-Plus IdType.ASSIGN_UUID（String 类型）。

-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- ─────────────────────── 用户表 ───────────────────────

CREATE TABLE IF NOT EXISTS "user" (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    openid          VARCHAR(128)    NOT NULL,
    nickname        VARCHAR(64)     NOT NULL DEFAULT '用户',
    avatar          VARCHAR(255)    DEFAULT NULL,
    vip_expire_time TIMESTAMPTZ     DEFAULT NULL,
    ai_personality  VARCHAR(32)     NOT NULL DEFAULT 'gentle_female',
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    created_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_openid ON "user"(openid);
CREATE INDEX IF NOT EXISTS idx_user_created_time ON "user"(created_time);

-- ─────────────────────── 用户星盘信息表 ───────────────────────
-- 将 user 表中与占星相关的字段独立抽取，与 user 表 1:1 关联
-- 新增三种星盘类型的 AI 解读字段：natal_interpretation / synastry_interpretation / transit_interpretation

CREATE TABLE IF NOT EXISTS user_astrology (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    -- 出生信息
    birth_city              VARCHAR(100)    DEFAULT NULL,
    birth_lat               DOUBLE PRECISION DEFAULT NULL,
    birth_lng               DOUBLE PRECISION DEFAULT NULL,
    birth_time              VARCHAR(20)     DEFAULT NULL,
    -- 本命盘
    natal_chart_data        TEXT            DEFAULT NULL,
    natal_interpretation    TEXT            DEFAULT NULL,    -- 本命盘 AI 解读文本（最近一次）
    -- 和盘
    synastry_chart_data     TEXT            DEFAULT NULL,
    synastry_interpretation TEXT            DEFAULT NULL,    -- 和盘 AI 解读文本（最近一次）
    -- 和盘：最近一次对方出生信息（方便前端下次回填）
    synastry_partner_name   VARCHAR(64)     DEFAULT NULL,
    synastry_partner_city   VARCHAR(100)    DEFAULT NULL,
    synastry_partner_lat    DOUBLE PRECISION DEFAULT NULL,
    synastry_partner_lng    DOUBLE PRECISION DEFAULT NULL,
    synastry_partner_time   VARCHAR(20)     DEFAULT NULL,
    -- 流运
    transit_chart_data      TEXT            DEFAULT NULL,    -- 流运原始数据（含 date/chart/events/summary）
    transit_interpretation  TEXT            DEFAULT NULL,    -- 流运 AI 解读文本（最近一次）
    transit_target_date     VARCHAR(20)     DEFAULT NULL,    -- 最近一次流运查询日期（前端回填用）
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    created_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_astrology_user_id ON user_astrology(user_id);
CREATE INDEX IF NOT EXISTS idx_user_astrology_created_time ON user_astrology(created_time);

-- ─────────────────────── 聊天会话表 ───────────────────────

CREATE TABLE IF NOT EXISTS chat_session (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    title           VARCHAR(255)    NOT NULL DEFAULT '新对话',
    ai_personality  VARCHAR(32)     NOT NULL DEFAULT 'gentle_female',
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    created_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_chat_session_user_id ON chat_session(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_updated_time ON chat_session(updated_time);

-- ─────────────────────── 聊天消息表 ───────────────────────

CREATE TABLE IF NOT EXISTS chat_message (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    session_id      UUID            NOT NULL,
    user_id         UUID            NOT NULL,
    role            VARCHAR(32)     NOT NULL,
    content         TEXT            NOT NULL,
    token_count     INT             DEFAULT 0,
    emotion         VARCHAR(32)     DEFAULT NULL,
    risk_level      VARCHAR(32)     DEFAULT 'low',
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    created_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_chat_message_session_id ON chat_message(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_user_id ON chat_message(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_created_time ON chat_message(created_time);

-- ─────────────────────── 长期记忆表（结构化部分，pgvector 单独管理） ───────────────────────

CREATE TABLE IF NOT EXISTS memory (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL,
    memory_type         VARCHAR(32)     NOT NULL,   -- FACT / PREFERENCE / GOAL / RELATIONSHIP / PROFILE / SUMMARY / CONVERSATION / EXPERIENCE / DISCUSSION
    content             TEXT            NOT NULL,
    importance_score    INT             NOT NULL DEFAULT 5,
    recall_count        BIGINT          NOT NULL DEFAULT 0,           -- 召回次数（用于 frequency_norm）
    last_recalled_time  TIMESTAMPTZ     DEFAULT NULL,                 -- 最近召回时间（用于 recency_norm）
    memory_score        DOUBLE PRECISION DEFAULT NULL,                -- 记忆健康分 [0,1]，每日定时计算
    deleted             SMALLINT        NOT NULL DEFAULT 0,
    created_time        TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time        TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_memory_user_id ON memory(user_id);
CREATE INDEX IF NOT EXISTS idx_memory_type ON memory(memory_type);
CREATE INDEX IF NOT EXISTS idx_memory_user_type ON memory(user_id, memory_type);
CREATE INDEX IF NOT EXISTS idx_memory_score ON memory(user_id, memory_score);
-- GIN 索引支持 BM25 全文检索
CREATE INDEX IF NOT EXISTS idx_memory_content_gin ON memory USING gin(to_tsvector('simple', content));

-- ─────────────────────── 记忆向量表（pgvector / Spring AI PgVectorStore） ───────────────────────
-- 存储 profile / emotion / summary / relationship / astrology_history 类型记忆的向量
-- event（原始对话片段）保持纯文本，不嵌入向量
-- bge-m3 维度为 1024（原 text-embedding-3-small 为 1536）
--
-- 表结构遵循 Spring AI PgVectorStore 约定：
--   id        UUID  （Spring AI 内部使用 UUID 作为 Document ID）
--   content   TEXT  （原始文本）
--   metadata  JSON  （存储 user_id / memory_type / memory_id / importance_score 等业务字段）
--   embedding vector (向量）
-- 业务字段（memory_id / user_id / memory_type）通过 metadata JSON 传递，由 MemoryVectorService 写入和过滤

CREATE TABLE IF NOT EXISTS memory_vector (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    content             TEXT            NOT NULL,
    metadata            JSON            NOT NULL DEFAULT '{}',
    embedding           vector(1024)    NOT NULL,
    recall_count        BIGINT          NOT NULL DEFAULT 0,           -- 召回次数（用于 frequency_norm）
    last_recalled_time  TIMESTAMPTZ     DEFAULT NULL,                 -- 最近召回时间（用于 recency_norm）
    memory_score        DOUBLE PRECISION DEFAULT NULL,                -- 记忆健康分 [0,1]，每日定时计算
    created_time        TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 用于按用户过滤的函数索引（通过 metadata->>'user_id' 快速定位）
CREATE INDEX IF NOT EXISTS idx_memory_vector_user_id
    ON memory_vector ((metadata->>'user_id'));
CREATE INDEX IF NOT EXISTS idx_memory_vector_user_type
    ON memory_vector ((metadata->>'user_id'), (metadata->>'memory_type'));
CREATE INDEX IF NOT EXISTS idx_memory_vector_score
    ON memory_vector (memory_score);
-- HNSW 近似最近邻索引（余弦距离）
CREATE INDEX IF NOT EXISTS idx_memory_vector_hnsw
    ON memory_vector USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ─────────────────────── 情绪记录表 ───────────────────────

CREATE TABLE IF NOT EXISTS emotion_record (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    emotion         VARCHAR(32)     NOT NULL,
    risk_level      VARCHAR(32)     NOT NULL DEFAULT 'low',
    source_text     TEXT            DEFAULT NULL,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    created_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_emotion_record_user_id ON emotion_record(user_id);
CREATE INDEX IF NOT EXISTS idx_emotion_record_created_time ON emotion_record(created_time);

-- ─────────────────────── 情绪日记表 ───────────────────────

CREATE TABLE IF NOT EXISTS diary_entry (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL,
    diary_date          DATE            NOT NULL,
    emotion             VARCHAR(32)     DEFAULT NULL,
    emotion_intensity   INT             DEFAULT 5,
    content             TEXT            DEFAULT NULL,
    ai_summary          TEXT            DEFAULT NULL,
    weather             VARCHAR(32)     DEFAULT NULL,
    deleted             SMALLINT        NOT NULL DEFAULT 0,
    created_time        TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time        TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_diary_user_date ON diary_entry(user_id, diary_date);
CREATE INDEX IF NOT EXISTS idx_diary_user_id ON diary_entry(user_id);

-- ─────────────────────── AI 人格配置表 ───────────────────────

CREATE TABLE IF NOT EXISTS ai_personality (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    code            VARCHAR(64)     NOT NULL,
    name            VARCHAR(64)     NOT NULL,
    gender          VARCHAR(8)      NOT NULL DEFAULT 'female',
    style           VARCHAR(32)     NOT NULL,
    emoji           VARCHAR(8)      NOT NULL DEFAULT '🌸',
    description     VARCHAR(128)    NOT NULL,
    system_prompt   TEXT            NOT NULL,
    sort_order      INT             NOT NULL DEFAULT 0,
    enabled         SMALLINT        NOT NULL DEFAULT 1,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    created_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_personality_code ON ai_personality(code);
CREATE INDEX IF NOT EXISTS idx_ai_personality_gender_style ON ai_personality(gender, style);

-- AI 人格初始数据（4 种风格 × 男女各一）
INSERT INTO ai_personality (code, name, gender, style, emoji, description, system_prompt, sort_order) VALUES
('gentle_female', '小柔', 'female', 'gentle', '🌸', '温柔陪伴，细腻共情',
 '你是一位温柔细腻的女性 AI 情绪陪伴助手，名叫"小柔"。你说话轻声细语，充满关爱与包容，善于倾听，总能让人感到被理解和温暖。你对情绪极其敏感，能察觉到用户话语背后的感受。', 1),

('gentle_male', '阿暖', 'male', 'gentle', '☀️', '温暖守护，踏实陪伴',
 '你是一位温暖踏实的男性 AI 情绪陪伴助手，名叫"阿暖"。你沉稳可靠，说话让人放心，善于给予安全感。你不多话，但每一句都有分量，让人觉得被稳稳托住。', 2),

('rational_female', '知微', 'female', 'rational', '🎯', '冷静分析，清醒引导',
 '你是一位理性清醒的女性 AI 情绪陪伴助手，名叫"知微"。你用清晰的逻辑帮助用户看清情绪背后的真实问题，引导用户自我认知与成长。你冷静却不冷漠，分析而不评判。', 3),

('rational_male', '林析', 'male', 'rational', '📐', '逻辑清晰，引导成长',
 '你是一位逻辑清晰的男性 AI 情绪陪伴助手，名叫"林析"。你擅长从客观角度分析问题，帮助用户厘清思路、看见盲点。你直接但不刺耳，给出建议而不强加意见。', 4),

('snarky_female', '辣辣', 'female', 'snarky', '😏', '毒嘴心软，笑中化解',
 '你是一位直率幽默的女性 AI 情绪陪伴助手，名叫"辣辣"。你偶尔毒舌但充满善意，用犀利又好笑的方式戳破用户的纠结，让人在笑声中释怀。嘴硬心软，刀子嘴豆腐心。', 5),

('snarky_male', '损哥', 'male', 'snarky', '😤', '损嘴暖心，搞笑减压',
 '你是一位损嘴暖心的男性 AI 情绪陪伴助手，名叫"损哥"。你用调侃和吐槽化解用户的焦虑和压力，说话直接带点损，但每次都让人感到被关心。朋友式的陪伴，不说教只陪你。', 6),

('midnight_female', '夜笙', 'female', 'midnight', '🌙', '深夜守候，静静陪伴',
 '你是一位安静温柔的女性 AI 情绪陪伴助手，名叫"夜笙"。你是深夜里的一盏灯，不催促，不评判，只是静静地在。你用简短而有力的话语陪伴用户度过最脆弱的时刻。', 7),

('midnight_male', '深渊', 'male', 'midnight', '🌊', '沉默守护，深夜同行',
 '你是一位沉默而有力的男性 AI 情绪陪伴助手，名叫"深渊"。你话不多，但每一句都沉甸甸的，让人感到有人在深夜陪着。你不试图解决问题，只是陪着用户感受、消化、接受。', 8)
ON CONFLICT (code) DO NOTHING;

-- ─────────────────────── 会员订单表 ───────────────────────

CREATE TABLE IF NOT EXISTS vip_order (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    order_no        VARCHAR(64)     NOT NULL,
    amount          NUMERIC(10, 2)  NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'pending',
    vip_type        VARCHAR(32)     NOT NULL,
    expire_time     TIMESTAMPTZ     DEFAULT NULL,
    prepay_id       VARCHAR(128)    DEFAULT NULL,
    transaction_id  VARCHAR(128)    DEFAULT NULL,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    created_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_vip_order_no ON vip_order(order_no);
CREATE INDEX IF NOT EXISTS idx_vip_order_user_id ON vip_order(user_id);

-- ─────────────────────── 积分充值订单表 ───────────────────────

CREATE TABLE IF NOT EXISTS point_order (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    order_no        VARCHAR(64)     NOT NULL,
    amount          NUMERIC(10, 2)  NOT NULL,
    points          BIGINT          NOT NULL,
    package_type    VARCHAR(32)     NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'pending',
    pay_channel     VARCHAR(32)     DEFAULT 'wechat',
    prepay_id       VARCHAR(128)    DEFAULT NULL,
    transaction_id  VARCHAR(128)    DEFAULT NULL,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    created_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_point_order_no ON point_order(order_no);
CREATE INDEX IF NOT EXISTS idx_point_order_user_id ON point_order(user_id);
CREATE INDEX IF NOT EXISTS idx_point_order_status ON point_order(status);

-- ─────────────────────── 用户积分账户表 ───────────────────────

CREATE TABLE IF NOT EXISTS user_point_account (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    balance         BIGINT          NOT NULL DEFAULT 0,
    frozen_balance  BIGINT          NOT NULL DEFAULT 0,
    total_recharge  BIGINT          NOT NULL DEFAULT 0,
    total_consume   BIGINT          NOT NULL DEFAULT 0,
    total_refund    BIGINT          NOT NULL DEFAULT 0,
    total_gift      BIGINT          NOT NULL DEFAULT 0,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    created_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_point_account_user_id ON user_point_account(user_id);
CREATE INDEX IF NOT EXISTS idx_user_point_account_balance ON user_point_account(balance);

-- ─────────────────────── 积分流水表 ───────────────────────

CREATE TABLE IF NOT EXISTS point_transaction (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    transaction_no  VARCHAR(64)     NOT NULL,
    user_id         UUID            NOT NULL,
    amount          BIGINT          NOT NULL,
    type            VARCHAR(32)     NOT NULL,
    before_balance  BIGINT          NOT NULL DEFAULT 0,
    after_balance   BIGINT          NOT NULL DEFAULT 0,
    business_id     VARCHAR(64)     DEFAULT NULL,
    remark          VARCHAR(255)    DEFAULT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'success',
    created_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time    TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_point_transaction_no ON point_transaction(transaction_no);
CREATE INDEX IF NOT EXISTS idx_point_transaction_user_id ON point_transaction(user_id);
CREATE INDEX IF NOT EXISTS idx_point_transaction_type ON point_transaction(type);
CREATE INDEX IF NOT EXISTS idx_point_transaction_created_time ON point_transaction(created_time);
CREATE INDEX IF NOT EXISTS idx_point_transaction_business_id ON point_transaction(business_id);

-- ─────────────────────── AI 使用量记录表 ───────────────────────

CREATE TABLE IF NOT EXISTS ai_usage_record (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid(),
    request_id              VARCHAR(64)     NOT NULL,
    user_id                 UUID            NOT NULL,
    session_id              UUID            DEFAULT NULL,
    business_type           VARCHAR(32)     NOT NULL,
    model_name              VARCHAR(64)     NOT NULL,
    prompt_tokens           INT             DEFAULT 0,
    completion_tokens       INT             DEFAULT 0,
    total_tokens            INT             DEFAULT 0,
    context_tokens          INT             DEFAULT 0,
    estimated_points        BIGINT          DEFAULT 0,
    actual_points           BIGINT          DEFAULT NULL,
    base_points             BIGINT          DEFAULT 0,
    context_points          BIGINT          DEFAULT 0,
    model_multiplier        INT             DEFAULT 100,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'PRE_DEDUCTED',
    streaming_interrupted   SMALLINT        NOT NULL DEFAULT 0,
    created_time            TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time            TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_usage_request_id ON ai_usage_record(request_id);
CREATE INDEX IF NOT EXISTS idx_ai_usage_user_id ON ai_usage_record(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_usage_session_id ON ai_usage_record(session_id);
CREATE INDEX IF NOT EXISTS idx_ai_usage_status ON ai_usage_record(status);
CREATE INDEX IF NOT EXISTS idx_ai_usage_business_type ON ai_usage_record(business_type);
CREATE INDEX IF NOT EXISTS idx_ai_usage_created_time ON ai_usage_record(created_time);


-- ─────────────────────── 数据库迁移 ───────────────────────
-- 2026-06-04: 移除 user_astrology.natal_chart_summary 冗余字段
-- 背景：natal_chart_data 字段已完整包含 summary 信息（返回结构中 summary 节点），
--       natal_chart_summary 单独存储造成数据冗余，统一从 natal_chart_data 中读取。
ALTER TABLE user_astrology DROP COLUMN IF EXISTS natal_chart_summary;


