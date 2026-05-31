package com.mindecho.common.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * MyBatis TypeHandler for java.util.UUID ↔ PostgreSQL uuid
 *
 * <p>PostgreSQL JDBC 驱动支持直接通过 setObject 传入 UUID，
 * ResultSet 也可通过 getObject 返回 UUID，因此此 Handler 直接委托给 JDBC 驱动处理。
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object obj = rs.getObject(columnName);
        if (obj == null) return null;
        if (obj instanceof UUID uuid) return uuid;
        return UUID.fromString(obj.toString());
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object obj = rs.getObject(columnIndex);
        if (obj == null) return null;
        if (obj instanceof UUID uuid) return uuid;
        return UUID.fromString(obj.toString());
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object obj = cs.getObject(columnIndex);
        if (obj == null) return null;
        if (obj instanceof UUID uuid) return uuid;
        return UUID.fromString(obj.toString());
    }
}

