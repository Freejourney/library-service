package com.elibrary.lending.sqlite;

import com.elibrary.common.ApiException;
import com.elibrary.common.OperationResult;
import com.elibrary.common.Paging;
import com.elibrary.lending.application.BorrowLoan;
import com.elibrary.lending.application.IdempotencyQueryService;
import com.elibrary.lending.application.LoanQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 场景 5：个人借阅记录 + 场景 6：幂等操作查询（对应 curl-scenarios.md 第 5/6 章）。 */
class LoanQueryScenarioTest extends SqliteScenarioTestBase {

    @Autowired
    private BorrowLoan borrower;

    @Autowired
    private LoanQueryService loanQueries;

    @Autowired
    private IdempotencyQueryService operations;

    @Test
    void myLoansList() {
        Paging<Map<String, Object>> page = loanQueries.myLoans(U1, U1.toString(), null, null, null);
        assertEquals(0, page.total()); // U1 无借阅
    }

    @Test
    void myLoansActiveFilter() {
        Paging<Map<String, Object>> page = loanQueries.myLoans(U4, U4.toString(), "ACTIVE", null, null);
        assertEquals(5, page.total());
        assertTrue(page.items().stream().allMatch(m -> m.get("status").equals("ACTIVE")));
    }

    @Test
    void myLoansOverdueFilter() {
        Paging<Map<String, Object>> page = loanQueries.myLoans(U3, U3.toString(), "OVERDUE", null, null);
        assertEquals(1, page.total()); // 仅 ACTIVE 逾期那条
        Map<String, Object> row = page.items().get(0);
        assertEquals("OVERDUE", row.get("status"));
        assertEquals("OVERDUE_DAYS", row.get("dayCountType"));
    }

    @Test
    void myLoansCompletedFilter() {
        Paging<Map<String, Object>> page = loanQueries.myLoans(U3, U3.toString(), "COMPLETED", null, null);
        assertEquals(1, page.total()); // RETURNED_LATE 那条
    }

    @Test
    void myLoansInvalidStatusRejected() {
        assertThrows(ApiException.class,
                () -> loanQueries.myLoans(U1, U1.toString(), "BOGUS", null, null));
    }

    @Test
    void myLoansNotOwnerNotFound() {
        // U1 查询 U2 的列表 -> 404
        assertThrows(ApiException.class,
                () -> loanQueries.myLoans(U1, U2.toString(), null, null, null));
    }

    @Test
    void loanDetailOwned() {
        Map<String, Object> row = loanQueries.loan(U3, "00000000-0000-0000-0000-000000000031");
        assertEquals("OVERDUE", row.get("status"));
        assertEquals("Quantum Physics", row.get("title"));
    }

    @Test
    void loanDetailNotOwnedNotFound() {
        assertThrows(ApiException.class,
                () -> loanQueries.loan(U1, "00000000-0000-0000-0000-000000000031"));
    }

    @Test
    void operationQueryFound() {
        String key = "key-op-query";
        borrower.borrow(U1, B1.toString(), key);
        Map<String, Object> result = operations.operation(U1, key);
        assertEquals("COMPLETED", result.get("state"));
        assertEquals(201, result.get("httpStatus"));
    }

    @Test
    void operationQueryNotFound() {
        assertThrows(ApiException.class,
                () -> operations.operation(U1, "nonexistent-key"));
    }

    @Test
    void operationQueryOthersNotFound() {
        String key = "key-op-other";
        borrower.borrow(U1, B1.toString(), key);
        // U2 查询 U1 的 key -> 404
        assertThrows(ApiException.class,
                () -> operations.operation(U2, key));
    }
}