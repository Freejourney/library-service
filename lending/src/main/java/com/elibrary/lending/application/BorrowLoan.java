package com.elibrary.lending.application;

import com.elibrary.account.application.AccountAccessPort;
import com.elibrary.account.domain.ReaderAccount;
import com.elibrary.catalog.application.CatalogInventoryPort;
import com.elibrary.catalog.application.CatalogReadPort;
import com.elibrary.catalog.domain.CatalogItem;
import com.elibrary.common.ApiException;
import com.elibrary.common.Ids;
import com.elibrary.common.Maps;
import com.elibrary.common.OperationResult;
import com.elibrary.common.Times;
import com.elibrary.lending.domain.DueDateCalculator;
import com.elibrary.lending.domain.Loan;
import com.elibrary.lending.domain.LoanPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** 借阅用例：幂等 -> 锁读者 -> 规则校验 -> 锁条目 -> 插 Loan + 扣库存，同事务提交。 */
@Service
public class BorrowLoan {

    private final LoanRepository loans;
    private final AccountAccessPort accounts;
    private final CatalogReadPort catalog;
    private final CatalogInventoryPort inventory;
    private final IdempotencyService idempotency;
    private final LendingPolicyService policies;
    private final Clock clock;

    public BorrowLoan(LoanRepository loans, AccountAccessPort accounts, CatalogReadPort catalog,
                      CatalogInventoryPort inventory, IdempotencyService idempotency,
                      LendingPolicyService policies, Clock clock) {
        this.loans = loans;
        this.accounts = accounts;
        this.catalog = catalog;
        this.inventory = inventory;
        this.idempotency = idempotency;
        this.policies = policies;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OperationResult borrow(UUID accountId, String itemId, String key) {
        UUID itemUuid = Ids.parse(itemId);
        idempotency.validateKey(key);
        String operation = "BORROW";
        byte[] hash = sha256(itemUuid.toString());
        idempotency.deleteExpired(accountId, operation, key);

        OperationResult replayed = idempotency.lookup(accountId, operation, key, hash);
        if (replayed != null) {
            return replayed;
        }

        ReaderAccount account = accounts.lockById(accountId)
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND"));
        replayed = idempotency.lookup(accountId, operation, key, hash);
        if (replayed != null) {
            return replayed;
        }
        if (!account.eligibleForBorrowing()) {
            throw new ApiException(403, "ACCOUNT_NOT_ELIGIBLE", "Account is not eligible");
        }

        LocalDateTime now = Times.utc(clock);
        if (loans.countOverdue(accountId, now) > 0) {
            throw ApiException.conflict("OVERDUE_LOANS_EXIST");
        }
        LoanPolicy policy = policies.policy();
        if (loans.countActive(accountId) >= policy.maxActive()) {
            throw ApiException.conflict("LOAN_LIMIT_EXCEEDED");
        }
        if (loans.countDuplicate(accountId, itemUuid) > 0) {
            throw ApiException.conflict("DUPLICATE_ACTIVE_LOAN");
        }

        CatalogItem item = catalog.lockById(itemUuid)
                .orElseThrow(() -> ApiException.notFound("BOOK_NOT_FOUND"));
        if (item.shelfStatus() == com.elibrary.catalog.domain.ShelfStatus.REMOVED) {
            throw ApiException.notFound("BOOK_NOT_FOUND");
        }
        if (!item.borrowable()) {
            throw ApiException.conflict("BOOK_UNAVAILABLE");
        }

        Instant borrowed = clock.instant();
        Instant due = DueDateCalculator.calculate(borrowed, policy.days(), policy.dueTime(), policy.zone());
        Loan loan = new Loan(
                Ids.v7(), accountId, itemUuid, "ACTIVE",
                Times.toUtc(borrowed), Times.toUtc(due), null,
                item.title(), item.creator(), item.type(), policy.policyVersion(), null);
        try {
            loans.insert(loan);
        } catch (DuplicateKeyException exception) {
            throw ApiException.conflict("DUPLICATE_ACTIVE_LOAN");
        }
        if (!inventory.decrementCopies(itemUuid)) {
            throw ApiException.conflict("BOOK_UNAVAILABLE");
        }

        Map<String, Object> data = Maps.of(
                "id", loan.loanId(),
                "catalogItemId", itemUuid,
                "title", item.title(),
                "creator", item.creator(),
                "type", item.type(),
                "borrowedAt", borrowed.atZone(policy.zone()).toOffsetDateTime(),
                "dueAt", due.atZone(policy.zone()).toOffsetDateTime(),
                "status", "ACTIVE",
                "activeLoanCount", loans.countActive(accountId),
                "maxActiveLoans", policy.maxActive());
        idempotency.save(accountId, operation, key, hash, 201, data, policies.idempotencyRetentionDays());
        return new OperationResult(201, data, false);
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