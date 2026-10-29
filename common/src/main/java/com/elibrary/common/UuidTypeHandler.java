package com.elibrary.common;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import java.sql.*;
import java.util.UUID;

public class UuidTypeHandler extends BaseTypeHandler<UUID> {
    @Override public void setNonNullParameter(PreparedStatement ps, int i, UUID value, JdbcType jdbcType) throws SQLException { ps.setBytes(i, Ids.bytes(value)); }
    @Override public UUID getNullableResult(ResultSet rs, String column) throws SQLException { return read(rs.getBytes(column)); }
    @Override public UUID getNullableResult(ResultSet rs, int column) throws SQLException { return read(rs.getBytes(column)); }
    @Override public UUID getNullableResult(CallableStatement cs, int column) throws SQLException { return read(cs.getBytes(column)); }
    private UUID read(byte[] value) { return value == null ? null : Ids.fromBytes(value); }
}
