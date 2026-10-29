package com.elibrary.lending.sqlite;

import com.elibrary.account.infrastructure.AccountMapper;
import com.elibrary.account.infrastructure.AccountRow;
import com.elibrary.common.Ids;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.UUID;

/** SQLite 版 AccountMapper：实现主接口，用 JdbcTemplate 执行 SQLite 语法。 */
public class SqliteAccountMapper implements AccountMapper {

    private final JdbcTemplate jdbc;

    public SqliteAccountMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<AccountRow> ROW = (rs, i) -> new AccountRow(
            Ids.fromBytes(rs.getBytes("account_id")),
            rs.getString("status"),
            rs.getString("eligibility_level"),
            rs.getString("display_name"));

    @Override
    public AccountRow findById(UUID id) {
        return jdbc.query("""
                SELECT account_id,status,eligibility_level,display_name
                FROM accounts WHERE account_id=?
                """, ROW, Ids.bytes(id)).stream().findFirst().orElse(null);
    }

    @Override
    public AccountRow lockById(UUID id) {
        // SQLite 单写者，无需 FOR UPDATE
        return findById(id);
    }
}