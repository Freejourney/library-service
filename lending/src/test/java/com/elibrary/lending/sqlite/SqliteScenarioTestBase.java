package com.elibrary.lending.sqlite;

import com.elibrary.common.Ids;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SQLite 功能测试基类：加载完整 lending 应用上下文（含 account/catalog 实现），
 * 用内存 SQLite 覆盖数据源与 Mapper，按 curl-scenarios.md 0.1 准备测试数据。
 */
@SpringBootTest(classes = com.elibrary.lending.LendingApplication.class, properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://localhost:9443/jwks",
        "spring.security.oauth2.resourceserver.jwt.audiences=elibrary-test",
        "spring.flyway.enabled=false"
})
@Import(SqliteTestConfig.class)
@ActiveProfiles("test")
public abstract class SqliteScenarioTestBase {

    // 固定测试标识（与 curl-scenarios.md 一致）
    protected static final UUID U1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    protected static final UUID U2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    protected static final UUID U3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    protected static final UUID U4 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    protected static final UUID B1 = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    protected static final UUID B2 = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    protected static final UUID B3 = UUID.fromString("00000000-0000-0000-0000-00000000000d");
    protected static final UUID B4 = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    protected static final UUID B5 = UUID.fromString("00000000-0000-0000-0000-00000000000f");
    protected static final UUID B6 = UUID.fromString("00000000-0000-0000-0000-000000000016");
    protected static final UUID B7 = UUID.fromString("00000000-0000-0000-0000-000000000017");
    protected static final UUID B8 = UUID.fromString("00000000-0000-0000-0000-000000000018");
    protected static final UUID B9 = UUID.fromString("00000000-0000-0000-0000-000000000019");
    protected static final UUID B10 = UUID.fromString("00000000-0000-0000-0000-00000000001a");
    protected static final UUID B11 = UUID.fromString("00000000-0000-0000-0000-00000000001b");
    protected static final UUID C1 = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    protected static final UUID C2 = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    protected static final UUID C3 = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM idempotency_records");
        jdbc.update("DELETE FROM loans");
        jdbc.update("DELETE FROM catalog_items");
        jdbc.update("DELETE FROM categories");
        jdbc.update("DELETE FROM accounts");
        seed();
    }

    /** 按 curl-scenarios.md 0.1 准备基线数据。 */
    private void seed() {
        String now = LocalDateTime.now().toString();
        // 账号：U1 NORMAL / U2 FROZEN / U3 NORMAL(有逾期) / U4 NORMAL(借满)
        insertAccount(U1, "NORMAL");
        insertAccount(U2, "FROZEN");
        insertAccount(U3, "NORMAL");
        insertAccount(U4, "NORMAL");

        // 分类：C1 Fiction(ACTIVE) / C2 Science(ACTIVE) / C3 Disabled(DISABLED)
        insertCategory(C1, "Fiction", "ACTIVE", 1);
        insertCategory(C2, "Science", "ACTIVE", 2);
        insertCategory(C3, "Disabled Cat", "DISABLED", 3);

        // 条目：B1 可借3 / B2 无库存 / B3 下架 / B4 移除 / B5 可借2 / B6 可借1
        insertItem(B1, "EBOOK", "The Great Book", C1, "the great book", "ON_SHELF", 3, 3);
        insertItem(B2, "EBOOK", "Out of Stock Book", C1, "out of stock book", "ON_SHELF", 0, 0);
        insertItem(B3, "PERIODICAL", "Off Shelf Magazine", C1, "off shelf magazine", "OFF_SHELF", 5, 5);
        insertItem(B4, "EBOOK", "Removed Book", C1, "removed book", "REMOVED", 2, 2);
        insertItem(B5, "EBOOK", "Quantum Physics", C2, "quantum physics", "ON_SHELF", 2, 2);
        insertItem(B6, "EBOOK", "Conflict Test Book", C1, "conflict test book", "ON_SHELF", 1, 1);
        insertItem(B7, "EBOOK", "Seventh Book", C2, "seventh book", "ON_SHELF", 1, 1);
        insertItem(B8, "EBOOK", "Eighth Book", C2, "eighth book", "ON_SHELF", 1, 1);
        insertItem(B9, "EBOOK", "Ninth Book", C2, "ninth book", "ON_SHELF", 1, 1);
        insertItem(B10, "EBOOK", "Tenth Book", C2, "tenth book", "ON_SHELF", 1, 1);


        // U3 逾期借阅：1 条 ACTIVE 逾期(B5) + 1 条已归还逾期(B1)
        insertLoan(UUID.fromString("00000000-0000-0000-0000-000000000031"), U3, B5, "ACTIVE",
                LocalDateTime.now().minusDays(20).toString(), LocalDateTime.now().minusDays(6).toString(), null,
                "Quantum Physics", "EBOOK");
        insertLoan(UUID.fromString("00000000-0000-0000-0000-000000000032"), U3, B1, "RETURNED_LATE",
                LocalDateTime.now().minusDays(20).toString(), LocalDateTime.now().minusDays(6).toString(),
                LocalDateTime.now().minusDays(1).toString(), "The Great Book", "EBOOK");

        // U4 借满 5 本（B7/B8/B9/B10/B11，不占用 B1/B2/B3/B4/B5/B6）
        insertLoan(UUID.fromString("00000000-0000-0000-0000-000000000041"), U4, B7, "ACTIVE",
                LocalDateTime.now().minusDays(2).toString(), LocalDateTime.now().plusDays(12).toString(), null,
                "Seventh Book", "EBOOK");
        insertLoan(UUID.fromString("00000000-0000-0000-0000-000000000042"), U4, B8, "ACTIVE",
                LocalDateTime.now().minusDays(2).toString(), LocalDateTime.now().plusDays(12).toString(), null,
                "Eighth Book", "EBOOK");
        insertLoan(UUID.fromString("00000000-0000-0000-0000-000000000043"), U4, B9, "ACTIVE",
                LocalDateTime.now().minusDays(2).toString(), LocalDateTime.now().plusDays(12).toString(), null,
                "Ninth Book", "EBOOK");
        insertLoan(UUID.fromString("00000000-0000-0000-0000-000000000044"), U4, B10, "ACTIVE",
                LocalDateTime.now().minusDays(2).toString(), LocalDateTime.now().plusDays(12).toString(), null,
                "Tenth Book", "EBOOK");
        insertLoan(UUID.fromString("00000000-0000-0000-0000-000000000045"), U4, B4, "ACTIVE",
                LocalDateTime.now().minusDays(2).toString(), LocalDateTime.now().plusDays(12).toString(), null,
                "Removed Book", "EBOOK");

        // 扣库存：U3 占用 B5 1本、B1 1本；U4 占用 B7/B8/B9/B10/B4 各1本
        decrement(B5, 1);
        decrement(B1, 1);
        decrement(B7, 1);
        decrement(B8, 1);
        decrement(B9, 1);
        decrement(B10, 1);
        decrement(B4, 1);
    }

    protected void insertAccount(UUID id, String status) {
        jdbc.update("""
                INSERT INTO accounts(account_id,status,eligibility_level,display_name,created_at,updated_at)
                VALUES(?,?,'STANDARD',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, Ids.bytes(id), status, "user-" + id.toString().substring(0, 8));
    }

    protected void insertCategory(UUID id, String name, String status, int sortOrder) {
        jdbc.update("""
                INSERT INTO categories(category_id,name,status,sort_order,created_at,updated_at)
                VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, Ids.bytes(id), name, status, sortOrder);
    }

    protected void insertItem(UUID id, String type, String title, UUID categoryId, String searchText,
                              String shelfStatus, int total, int available) {
        jdbc.update("""
                INSERT INTO catalog_items(item_id,type,title,creator,category_id,search_text,shelf_status,
                                          total_copies,available_copies,published_at,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, Ids.bytes(id), type, title, "Author " + title, Ids.bytes(categoryId),
                searchText, shelfStatus, total, available);
    }

    protected void insertLoan(UUID loanId, UUID accountId, UUID itemId, String status,
                              String borrowedAt, String dueAt, String returnedAt,
                              String title, String type) {
        jdbc.update("""
                INSERT INTO loans(loan_id,account_id,item_id,status,borrowed_at,due_at,returned_at,
                                  title_snapshot,creator_snapshot,type_snapshot,policy_version)
                VALUES(?,?,?,?,?,?,?,?,?,?,'v1')
                """, Ids.bytes(loanId), Ids.bytes(accountId), Ids.bytes(itemId), status,
                borrowedAt, dueAt, returnedAt, title, "Author " + title, type);
    }

    protected void decrement(UUID itemId, int count) {
        jdbc.update("""
                UPDATE catalog_items SET available_copies=available_copies-?
                WHERE item_id=?
                """, count, Ids.bytes(itemId));
    }

    protected long availableCopies(UUID itemId) {
        Long n = jdbc.queryForObject(
                "SELECT available_copies FROM catalog_items WHERE item_id=?",
                Long.class, Ids.bytes(itemId));
        return n == null ? -1 : n;
    }

    protected long loanCount(UUID accountId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM loans WHERE account_id=? AND returned_at IS NULL",
                Long.class, Ids.bytes(accountId));
        return n == null ? 0 : n;
    }
}