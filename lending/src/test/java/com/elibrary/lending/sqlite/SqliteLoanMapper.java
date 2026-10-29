package com.elibrary.lending.sqlite;

import com.elibrary.common.Ids;
import com.elibrary.lending.domain.Loan;
import com.elibrary.lending.infrastructure.LoanMapper;
import com.elibrary.lending.infrastructure.LoanRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** SQLite 版 LoanMapper：实现主接口，用 JdbcTemplate 执行 SQLite 语法。 */
public class SqliteLoanMapper implements LoanMapper {

    private final JdbcTemplate jdbc;

    public SqliteLoanMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<LoanRow> ROW = (rs, i) -> new LoanRow(
            Ids.fromBytes(rs.getBytes("loan_id")),
            Ids.fromBytes(rs.getBytes("account_id")),
            Ids.fromBytes(rs.getBytes("item_id")),
            rs.getString("status"),
            toLocalDateTime(rs, "borrowed_at"),
            toLocalDateTime(rs, "due_at"),
            toLocalDateTime(rs, "returned_at"),
            rs.getString("title_snapshot"),
            rs.getString("creator_snapshot"),
            rs.getString("type_snapshot"),
            rs.getString("policy_version"),
            rs.getString("catalog_status"));

    private static LocalDateTime toLocalDateTime(ResultSet rs, String col) throws SQLException {
        String v = rs.getString(col);
        if (v == null) {
            return null;
        }
        // SQLite CURRENT_TIMESTAMP 输出 'YYYY-MM-DD HH:MM:SS'，LocalDateTime.toString() 输出 'YYYY-MM-DDTHH:MM:SS'
        return LocalDateTime.parse(v.replace(' ', 'T'));
    }

    @Override
    public long countActive(UUID account) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM loans WHERE account_id=? AND returned_at IS NULL",
                Long.class, Ids.bytes(account));
        return n == null ? 0 : n;
    }

    @Override
    public long countOverdue(UUID account, LocalDateTime now) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM loans
                WHERE account_id=? AND returned_at IS NULL AND due_at <= ?
                """, Long.class, Ids.bytes(account), now.toString());
        return n == null ? 0 : n;
    }

    @Override
    public long countDuplicate(UUID account, UUID item) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM loans
                WHERE account_id=? AND item_id=? AND returned_at IS NULL
                """, Long.class, Ids.bytes(account), Ids.bytes(item));
        return n == null ? 0 : n;
    }

    @Override
    public LoanRow lockById(UUID loan, UUID account) {
        return jdbc.query("""
                SELECT loan_id,account_id,item_id,status,borrowed_at,due_at,returned_at,
                       title_snapshot,creator_snapshot,type_snapshot,policy_version,NULL catalog_status
                FROM loans WHERE loan_id=? AND account_id=?
                """, ROW, Ids.bytes(loan), Ids.bytes(account)).stream().findFirst().orElse(null);
    }

    @Override
    public int insert(Loan loan) {
        return jdbc.update("""
                INSERT INTO loans(loan_id,account_id,item_id,status,borrowed_at,due_at,
                                  title_snapshot,creator_snapshot,type_snapshot,policy_version)
                VALUES(?,?,?,'ACTIVE',?,?,
                       ?,?,?,?)
                """, Ids.bytes(loan.loanId()), Ids.bytes(loan.accountId()), Ids.bytes(loan.itemId()),
                loan.borrowedAt().toString(), loan.dueAt().toString(),
                loan.titleSnapshot(), loan.creatorSnapshot(), loan.typeSnapshot(), loan.policyVersion());
    }

    @Override
    public int markReturned(UUID loan, String status, LocalDateTime returnedAt) {
        return jdbc.update("""
                UPDATE loans SET status=?,returned_at=?,version=version+1
                WHERE loan_id=? AND returned_at IS NULL
                """, status, returnedAt.toString(), Ids.bytes(loan));
    }

    @Override
    public LoanRow findOne(UUID account, UUID loan, LocalDateTime now) {
        return jdbc.query("""
                SELECT l.loan_id,l.account_id,l.item_id,
                       CASE WHEN l.returned_at IS NULL AND l.due_at<=? THEN 'OVERDUE' ELSE l.status END status,
                       l.borrowed_at,l.due_at,l.returned_at,l.title_snapshot,l.creator_snapshot,
                       l.type_snapshot,l.policy_version,COALESCE(i.shelf_status,'REMOVED') catalog_status
                FROM loans l LEFT JOIN catalog_items i ON i.item_id=l.item_id
                WHERE l.account_id=? AND l.loan_id=?
                """, ROW, now.toString(), Ids.bytes(account), Ids.bytes(loan)).stream().findFirst().orElse(null);
    }

    @Override
    public List<LoanRow> findByAccount(UUID account, String state, LocalDateTime now,
                                       int limit, long offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT l.loan_id,l.account_id,l.item_id,
                       CASE WHEN l.returned_at IS NULL AND l.due_at<=? THEN 'OVERDUE' ELSE l.status END status,
                       l.borrowed_at,l.due_at,l.returned_at,l.title_snapshot,l.creator_snapshot,
                       l.type_snapshot,l.policy_version,COALESCE(i.shelf_status,'REMOVED') catalog_status
                FROM loans l LEFT JOIN catalog_items i ON i.item_id=l.item_id
                WHERE l.account_id=?
                """);
        List<Object> args = new java.util.ArrayList<>();
        args.add(now.toString());
        args.add(Ids.bytes(account));
        if ("ACTIVE".equals(state)) {
            sql.append(" AND l.returned_at IS NULL AND l.due_at>?");
            args.add(now.toString());
        } else if ("OVERDUE".equals(state)) {
            sql.append(" AND l.returned_at IS NULL AND l.due_at<=?");
            args.add(now.toString());
        } else if ("COMPLETED".equals(state)) {
            sql.append(" AND l.returned_at IS NOT NULL");
        }
        sql.append("""
                 ORDER BY CASE WHEN l.returned_at IS NULL THEN 0 ELSE 1 END,
                          CASE WHEN l.returned_at IS NULL THEN l.due_at END ASC,
                          CASE WHEN l.returned_at IS NOT NULL THEN l.returned_at END DESC,
                          l.loan_id ASC
                 LIMIT ? OFFSET ?
                """);
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), ROW, args.toArray());
    }

    @Override
    public long countByAccount(UUID account, String state, LocalDateTime now) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM loans WHERE account_id=?");
        List<Object> args = new java.util.ArrayList<>();
        args.add(Ids.bytes(account));
        if ("ACTIVE".equals(state)) {
            sql.append(" AND returned_at IS NULL AND due_at>?");
            args.add(now.toString());
        } else if ("OVERDUE".equals(state)) {
            sql.append(" AND returned_at IS NULL AND due_at<=?");
            args.add(now.toString());
        } else if ("COMPLETED".equals(state)) {
            sql.append(" AND returned_at IS NOT NULL");
        }
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }
}