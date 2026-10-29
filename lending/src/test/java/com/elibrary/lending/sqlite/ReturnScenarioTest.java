package com.elibrary.lending.sqlite;

import com.elibrary.common.ApiException;
import com.elibrary.common.OperationResult;
import com.elibrary.lending.application.BorrowLoan;
import com.elibrary.lending.application.ReturnLoan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 场景 4：归还（对应 curl-scenarios.md 第 4 章）。 */
class ReturnScenarioTest extends SqliteScenarioTestBase {

    @Autowired
    private BorrowLoan borrower;

    @Autowired
    private ReturnLoan returner;


    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(OperationResult r) {
        return (Map<String, Object>) r.data();
    }

    private static final String LOAN_31 = "00000000-0000-0000-0000-000000000031"; // U3 逾期 ACTIVE
    private static final String LOAN_32 = "00000000-0000-0000-0000-000000000032"; // U3 已归还逾期

    @Test
    void returnSuccessRestoresStock() {
        // U1 借 B1（库存 2->1），归还（1->2）
        OperationResult borrow = borrower.borrow(U1, B1.toString(), "key-borrow");
        String loanId = data(borrow).get("id").toString();
        assertEquals(1, availableCopies(B1));

        OperationResult result = returner.returnLoan(U1, loanId, "key-return");
        assertEquals(200, result.status());
        assertEquals("RETURNED", data(result).get("status"));
        assertEquals(2, availableCopies(B1));
        assertEquals(0, loanCount(U1));
    }

    @Test
    void duplicateReturnRejected() {
        OperationResult borrow = borrower.borrow(U1, B1.toString(), "key-borrow");
        String loanId = data(borrow).get("id").toString();
        returner.returnLoan(U1, loanId, "key-return-1");

        ApiException e = assertThrows(ApiException.class,
                () -> returner.returnLoan(U1, loanId, "key-return-2"));
        assertEquals(409, e.status());
        assertEquals("LOAN_ALREADY_RETURNED", e.code());
    }

    @Test
    void returnOthersLoanNotFound() {
        // U3 的逾期借阅，U1 归还 -> 404
        ApiException e = assertThrows(ApiException.class,
                () -> returner.returnLoan(U1, LOAN_31, "key-other"));
        assertEquals(404, e.status());
        assertEquals("LOAN_NOT_FOUND", e.code());
    }

    @Test
    void overdueReturnMarkedLate() {
        OperationResult result = returner.returnLoan(U3, LOAN_31, "key-overdue");
        assertEquals(200, result.status());
        assertEquals("RETURNED_LATE", data(result).get("status"));
        // 库存回补 B5: 1 -> 2
        assertEquals(2, availableCopies(B5));
    }

    @Test
    void returnUnknownLoanNotFound() {
        ApiException e = assertThrows(ApiException.class,
                () -> returner.returnLoan(U1, "00000000-0000-0000-0000-0000000000ff", "key-none"));
        assertEquals(404, e.status());
        assertEquals("LOAN_NOT_FOUND", e.code());
    }

    @Test
    void returnRemovedItemStillWorks() {
        // U4 借了 B4(REMOVED)，归还应成功；库存回补（incrementCopies 不检查 shelf_status）
        OperationResult result = returner.returnLoan(U4, "00000000-0000-0000-0000-000000000045", "key-removed");
        assertEquals(200, result.status());
        assertEquals("RETURNED", data(result).get("status"));
        assertEquals(2, availableCopies(B4)); // 1 -> 2
    }
}