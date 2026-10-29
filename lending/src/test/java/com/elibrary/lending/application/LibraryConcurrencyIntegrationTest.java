package com.elibrary.lending.application;

import com.elibrary.common.ApiException;
import com.elibrary.common.Ids;
import com.elibrary.common.OperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://localhost:9443/jwks",
        "spring.security.oauth2.resourceserver.jwt.audiences=elibrary-test"
})
class LibraryConcurrencyIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("elibrary_db")
            .withUsername("elibrary")
            .withPassword("elibrary-test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BorrowLoan borrower;

    private UUID itemId;

    @BeforeEach
    void prepareDatabase() {
        jdbc.update("DELETE FROM idempotency_records");
        jdbc.update("DELETE FROM loans");
        jdbc.update("DELETE FROM catalog_items");
        jdbc.update("DELETE FROM categories");
        jdbc.update("DELETE FROM accounts");

        UUID categoryId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO categories(category_id,name,status,sort_order,created_at,updated_at)
                VALUES(?,?,'ACTIVE',0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, Ids.bytes(categoryId), "Concurrency test category");
        jdbc.update("""
                INSERT INTO catalog_items(item_id,type,title,category_id,search_text,shelf_status,
                                          total_copies,available_copies,created_at,updated_at)
                VALUES(?,'EBOOK','Concurrency test book',?,'concurrency test book','ON_SHELF',
                       1,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, Ids.bytes(itemId), Ids.bytes(categoryId));
    }

    @Test
    void simultaneousBorrowersCannotTakeTheLastCopy() throws Exception {
        int borrowerCount = 8;
        List<UUID> accounts = new ArrayList<>();
        for (int i = 0; i < borrowerCount; i++) {
            UUID accountId = UUID.randomUUID();
            accounts.add(accountId);
            jdbc.update("""
                    INSERT INTO accounts(account_id,status,eligibility_level,created_at,updated_at)
                    VALUES(?,'NORMAL','STANDARD',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                    """, Ids.bytes(accountId));
        }

        ExecutorService executor = Executors.newFixedThreadPool(borrowerCount);
        CountDownLatch ready = new CountDownLatch(borrowerCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> attempts = accounts.stream().map(accountId -> executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    return borrower.borrow(accountId, itemId.toString(), UUID.randomUUID().toString());
                } catch (ApiException exception) {
                    return exception;
                }
            })).toList();

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Borrower threads did not become ready");
            start.countDown();
            int successes = 0;
            int unavailable = 0;
            for (Future<Object> attempt : attempts) {
                Object result = attempt.get(30, TimeUnit.SECONDS);
                if (result instanceof OperationResult operation) {
                    assertEquals(201, operation.status());
                    successes++;
                } else {
                    ApiException exception = (ApiException) result;
                    assertEquals("BOOK_UNAVAILABLE", exception.code());
                    unavailable++;
                }
            }

            assertEquals(1, successes);
            assertEquals(borrowerCount - 1, unavailable);
            assertEquals(0, jdbc.queryForObject(
                    "SELECT available_copies FROM catalog_items WHERE item_id=?", Integer.class, Ids.bytes(itemId)));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM loans WHERE item_id=?", Integer.class, Ids.bytes(itemId)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void retryWithSameKeyReplaysTheCommittedBorrow() {
        UUID accountId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts(account_id,status,eligibility_level,created_at,updated_at)
                VALUES(?,'NORMAL','STANDARD',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, Ids.bytes(accountId));
        String key = UUID.randomUUID().toString();

        OperationResult first = borrower.borrow(accountId, itemId.toString(), key);
        OperationResult replay = borrower.borrow(accountId, itemId.toString(), key);

        assertEquals(201, first.status());
        assertEquals(first.data(), replay.data());
        assertTrue(replay.replay());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM loans WHERE account_id=?", Integer.class, Ids.bytes(accountId)));
        assertEquals(0, jdbc.queryForObject(
                "SELECT available_copies FROM catalog_items WHERE item_id=?", Integer.class, Ids.bytes(itemId)));
    }
}
