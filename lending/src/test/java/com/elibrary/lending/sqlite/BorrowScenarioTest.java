package com.elibrary.lending.sqlite;

import com.elibrary.common.ApiException;
import com.elibrary.common.OperationResult;
import com.elibrary.lending.application.BorrowLoan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 场景 3：借阅（对应 curl-scenarios.md 第 3 章）。 */
class BorrowScenarioTest extends SqliteScenarioTestBase {

    @Autowired
    private BorrowLoan borrower;

    @Test
    void borrowSuccess() {
        OperationResult result = borrower.borrow(U1, B1.toString(), "key-borrow-1");
        assertEquals(201, result.status());
        assertFalse(result.replay());
        // 库存 2 -> 1（U3 占用 1 本）
        assertEquals(1, availableCopies(B1));
        assertEquals(1, loanCount(U1));
    }

    @Test
    void idempotentReplayReturnsSameResult() {
        String key = "key-replay";
        OperationResult first = borrower.borrow(U1, B5.toString(), key);
        OperationResult replay = borrower.borrow(U1, B5.toString(), key);
        assertEquals(201, first.status());
        assertEquals(201, replay.status());
        assertTrue(replay.replay());
        // 重放返回存储的响应（首次实时计算含纳秒，重放为序列化值，比较关键字段）
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> firstData = (java.util.Map<String, Object>) first.data();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> replayData = (java.util.Map<String, Object>) replay.data();
        assertEquals(firstData.get("id").toString(), replayData.get("id").toString());
        assertEquals(firstData.get("catalogItemId").toString(), replayData.get("catalogItemId").toString());
        assertEquals(firstData.get("status"), replayData.get("status"));
        assertEquals(1, loanCount(U1));
    }

    @Test
    void sameKeyDifferentPayloadRejected() {
        String key = "key-conflict";
        assertEquals(201, borrower.borrow(U1, B6.toString(), key).status());
        ApiException e = assertThrows(ApiException.class,
                () -> borrower.borrow(U1, B5.toString(), key));
        assertEquals("IDEMPOTENCY_KEY_REUSED", e.code());
    }

    @Test
    void missingKeyRejected() {
        ApiException e = assertThrows(ApiException.class,
                () -> borrower.borrow(U1, B1.toString(), null));
        assertEquals(400, e.status());
        assertEquals("INVALID_IDEMPOTENCY_KEY", e.code());
    }

    @Test
    void frozenAccountForbidden() {
        ApiException e = assertThrows(ApiException.class,
                () -> borrower.borrow(U2, B1.toString(), "key-u2"));
        assertEquals(403, e.status());
        assertEquals("ACCOUNT_NOT_ELIGIBLE", e.code());
    }

    @Test
    void overdueAccountRejected() {
        ApiException e = assertThrows(ApiException.class,
                () -> borrower.borrow(U3, B1.toString(), "key-u3"));
        assertEquals(409, e.status());
        assertEquals("OVERDUE_LOANS_EXIST", e.code());
    }

    @Test
    void maxedOutAccountRejected() {
        ApiException e = assertThrows(ApiException.class,
                () -> borrower.borrow(U4, B5.toString(), "key-u4"));
        assertEquals(409, e.status());
        assertEquals("LOAN_LIMIT_EXCEEDED", e.code());
    }

    @Test
    void noStockRejected() {
        ApiException e = assertThrows(ApiException.class,
                () -> borrower.borrow(U1, B2.toString(), "key-b2"));
        assertEquals(409, e.status());
        assertEquals("BOOK_UNAVAILABLE", e.code());
    }

    @Test
    void offShelfRejected() {
        ApiException e = assertThrows(ApiException.class,
                () -> borrower.borrow(U1, B3.toString(), "key-b3"));
        assertEquals(409, e.status());
        assertEquals("BOOK_UNAVAILABLE", e.code());
    }

    @Test
    void removedRejected() {
        ApiException e = assertThrows(ApiException.class,
                () -> borrower.borrow(U1, B4.toString(), "key-b4"));
        assertEquals(404, e.status());
        assertEquals("BOOK_NOT_FOUND", e.code());
    }

    @Test
    void invalidItemIdRejected() {
        ApiException e = assertThrows(ApiException.class,
                () -> borrower.borrow(U1, "not-a-uuid", "key-bad"));
        assertEquals(400, e.status());
    }

    @Test
    void duplicateActiveLoanRejected() {
        // U1 先借 B1，再借 B1 -> DUPLICATE_ACTIVE_LOAN
        assertEquals(201, borrower.borrow(U1, B1.toString(), "key-dup-1").status());
        ApiException e = assertThrows(ApiException.class,
                () -> borrower.borrow(U1, B1.toString(), "key-dup-2"));
        assertEquals(409, e.status());
        assertEquals("DUPLICATE_ACTIVE_LOAN", e.code());
    }

    @Test
    void borrowUnknownAccountNotFound() {
        ApiException e = assertThrows(ApiException.class,
                () -> borrower.borrow(UUID.fromString("00000000-0000-0000-0000-0000000000ff"),
                        B1.toString(), "key-unknown"));
        assertEquals(404, e.status());
        assertEquals("ACCOUNT_NOT_FOUND", e.code());
    }
}