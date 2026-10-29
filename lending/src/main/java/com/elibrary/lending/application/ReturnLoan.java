package com.elibrary.lending.application;

import com.elibrary.catalog.application.CatalogInventoryPort;
import com.elibrary.catalog.application.CatalogReadPort;
import com.elibrary.common.ApiException;
import com.elibrary.common.Ids;
import com.elibrary.common.Maps;
import com.elibrary.common.OperationResult;
import com.elibrary.common.Times;
import com.elibrary.lending.domain.Loan;
import com.elibrary.lending.domain.LoanPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** 归还用例：锁 Loan -> 写归还状态 -> 回补库存，同事务提交；下架/移除条目仍可归还。 */
@Service
public class ReturnLoan {

    private final LoanRepository loans;
    private final CatalogReadPort catalog;
    private final CatalogInventoryPort inventory;
    private final IdempotencyService idempotency;
    private final LendingPolicyService policies;
    private final Clock clock;

    public ReturnLoan(LoanRepository loans, CatalogReadPort catalog, CatalogInventoryPort inventory,
                      IdempotencyService idempotency, LendingPolicyService policies, Clock clock) {
        this.loans = loans;
        this.catalog = catalog;
        this.inventory = inventory;
        this.idempotency = idempotency;
        this.policies = policies;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OperationResult returnLoan(UUID accountId, String loanId, String key) {
        UUID loanUuid = Ids.parse(loanId);
        idempotency.validateKey(key);
        String operation = "RETURN:" + loanUuid;
        byte[] hash = sha256(loanUuid.toString());
        idempotency.deleteExpired(accountId, operation, key);

        OperationResult replayed = idempotency.lookup(accountId, operation, key, hash);
        if (replayed != null) {
            return replayed;
        }

        Loan loan = loans.lockById(loanUuid, accountId)
                .orElseThrow(() -> ApiException.notFound("LOAN_NOT_FOUND"));
        replayed = idempotency.lookup(accountId, operation, key, hash);
        if (replayed != null) {
            return replayed;
        }
        if (loan.returned()) {
            throw ApiException.conflict("LOAN_ALREADY_RETURNED");
        }

        LocalDateTime returned = Times.utc(clock);
        String status = returned.isBefore(loan.dueAt()) ? "RETURNED" : "RETURNED_LATE";
        if (!loans.markReturned(loanUuid, status, returned)) {
            throw new IllegalStateException("Loan return update failed");
        }

        Integer availableCount = null;
        var lockedItem = catalog.lockById(loan.itemId());
        if (lockedItem.isPresent()) {
            if (!inventory.incrementCopies(loan.itemId())) {
                throw new IllegalStateException("Catalog inventory could not be restored");
            }
            availableCount = lockedItem.get().availableCopies() + 1;
        }

        LoanPolicy policy = policies.policy();
        Map<String, Object> data = Maps.of(
                "id", loanUuid,
                "status", status,
                "returnedAt", returned.atZone(policy.zone()).toOffsetDateTime(),
                "catalogItemId", loan.itemId(),
                "availableCount", availableCount,
                "activeLoanCount", loans.countActive(accountId));
        idempotency.save(accountId, operation, key, hash, 200, data, policies.idempotencyRetentionDays());
        return new OperationResult(200, data, false);
    }

    private static byte[] sha256(String value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}