package com.elibrary.lending.sqlite;

import com.elibrary.account.application.AccountApplicationService;
import com.elibrary.common.ApiException;
import com.elibrary.common.LendingPolicyPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/** 场景 1：账号资格（对应 curl-scenarios.md 第 1 章）。 */
class AccountEligibilityScenarioTest extends SqliteScenarioTestBase {

    @Autowired
    private AccountApplicationService accounts;

    @Autowired
    private LendingPolicyPort lendingPolicy;

    @Test
    void normalAccountIsEligible() {
        var me = accounts.getMyAccount(U1, 0, 0);
        assertEquals("NORMAL", me.status());
        assertTrue(me.borrowingEligible());
        assertTrue(me.eligibilityReasons().isEmpty());
        assertEquals(5, lendingPolicy.loanPolicy().maxActiveLoans());
    }

    @Test
    void frozenAccountIsNotEligible() {
        var me = accounts.getMyAccount(U2, 0, 0);
        assertEquals("FROZEN", me.status());
        assertFalse(me.borrowingEligible());
        assertTrue(me.eligibilityReasons().contains("FROZEN"));
    }

    @Test
    void accountWithOverdueLoansIsNotEligible() {
        var me = accounts.getMyAccount(U3, 1, 1);
        assertFalse(me.borrowingEligible());
        assertTrue(me.eligibilityReasons().contains("OVERDUE_LOANS_EXIST"));
    }

    @Test
    void unknownAccountReturnsNotFound() {
        assertThrows(ApiException.class,
                () -> accounts.getMyAccount(UUID_FF, 0, 0),
                "ACCOUNT_NOT_FOUND");
    }

    private static final java.util.UUID UUID_FF =
            java.util.UUID.fromString("00000000-0000-0000-0000-0000000000ff");
}