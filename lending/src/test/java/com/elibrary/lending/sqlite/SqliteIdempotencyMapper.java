package com.elibrary.lending.sqlite;

import com.elibrary.lending.infrastructure.IdempotencyMapper;
import com.elibrary.lending.infrastructure.IdempotencyRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.UUID;

/** SQLite 版 IdempotencyMapper：实现主接口，用 JdbcTemplate 执行 SQLite 语法。 */
public class SqliteIdempotencyMapper implements IdempotencyMapper {

    private final JdbcTemplate jdbc;

    public SqliteIdempotencyMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<IdempotencyRow> ROW = (rs, i) -> new IdempotencyRow(
            rs.getBytes("request_hash"),
            rs.getInt("http_status"),
            rs.getString("response_json"));

    @Override
    public IdempotencyRow find(UUID account, String operation, String key) {
        return jdbc.query("""
                SELECT request_hash,http_status,response_json FROM idempotency_records
                WHERE account_id=? AND operation=? AND idempotency_key=?
                  AND expires_at>datetime('now')
                """, ROW, IdsBytes(account), operation, key).stream().findFirst().orElse(null);
    }

    @Override
    public int deleteExpired(UUID account, String operation, String key) {
        return jdbc.update("""
                DELETE FROM idempotency_records
                WHERE account_id=? AND operation=? AND idempotency_key=?
                  AND expires_at<=datetime('now')
                """, IdsBytes(account), operation, key);
    }

    @Override
    public int save(UUID account, String operation, String key, byte[] hash,
                    int status, String json, int retention) {
        return jdbc.update("""
                INSERT INTO idempotency_records(account_id,operation,idempotency_key,request_hash,
                                                http_status,response_json,created_at,expires_at)
                VALUES(?,?,?,?,?,?,
                       datetime('now'),datetime('now', '+' || ? || ' days'))
                """, IdsBytes(account), operation, key, hash, status, json, retention);
    }

    @Override
    public IdempotencyRow findLatest(UUID account, String key) {
        return jdbc.query("""
                SELECT request_hash,http_status,response_json FROM idempotency_records
                WHERE account_id=? AND idempotency_key=? AND expires_at>datetime('now')
                ORDER BY created_at DESC LIMIT 1
                """, ROW, IdsBytes(account), key).stream().findFirst().orElse(null);
    }

    @Override
    public int deleteAllExpired() {
        return jdbc.update("DELETE FROM idempotency_records WHERE expires_at<datetime('now')");
    }

    private static byte[] IdsBytes(UUID id) {
        return com.elibrary.common.Ids.bytes(id);
    }
}