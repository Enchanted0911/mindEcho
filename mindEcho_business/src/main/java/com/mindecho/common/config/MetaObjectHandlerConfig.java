package com.mindecho.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * MyBatis Plus 自动填充配置（created_time / updated_time）
 *
 * <p>使用 {@link OffsetDateTime} 配合 Asia/Shanghai 时区，确保写入 PostgreSQL
 * {@code TIMESTAMPTZ} 字段时携带明确的 +08:00 时区信息，避免跨时区部署时的歧义。
 */
@Slf4j
@Component
public class MetaObjectHandlerConfig implements MetaObjectHandler {

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Override
    public void insertFill(MetaObject metaObject) {
        OffsetDateTime now = OffsetDateTime.now(ZONE_SHANGHAI);
        this.strictInsertFill(metaObject, "createdTime", OffsetDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedTime", OffsetDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedTime", OffsetDateTime.class,
                OffsetDateTime.now(ZONE_SHANGHAI));
    }
}

