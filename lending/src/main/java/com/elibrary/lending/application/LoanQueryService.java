package com.elibrary.lending.application;

import com.elibrary.common.ApiException;
import com.elibrary.common.Ids;
import com.elibrary.common.Maps;
import com.elibrary.common.Paging;
import com.elibrary.common.Times;
import com.elibrary.lending.domain.Loan;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 个人借阅记录查询：列表 / 单条详情 / 状态派生与剩余天数计算。 */
@Service
public class LoanQueryService implements LoanQueryPort {

    private static final Set<String> STATES = Set.of("ACTIVE", "OVERDUE", "COMPLETED");

    private final LoanRepository loans;
    private final LendingPolicyService policies;
    private final Clock clock;

    public LoanQueryService(LoanRepository loans, LendingPolicyService policies, Clock clock) {
        this.loans = loans;
        this.policies = policies;
        this.clock = clock;
    }

    public Paging<Map<String, Object>> myLoans(UUID accountId, String userId, String state,
                                               Integer page, Integer pageSize) {
        if (!accountId.equals(Ids.parse(userId))) {
            throw ApiException.notFound("LOAN_NOT_FOUND");
        }
        if (state != null && !STATES.contains(state)) {
            throw new ApiException(400, "INVALID_REQUEST", "Invalid status");
        }
        int p = page == null ? 1 : page;
        int size = pageSize == null ? 20 : pageSize;
        if (p < 1 || size < 1 || size > 100) {
            throw new ApiException(400, "INVALID_REQUEST", "Invalid page or pageSize");
        }
        LocalDateTime now = Times.utc(clock);
        long total = loans.countByAccount(accountId, state, now);
        int effectivePage = total == 0 ? 1 : (int) Math.min(p, Math.max(1, (total + size - 1) / size));
        List<Map<String, Object>> rows = loans.findByAccount(accountId, state, now, size, (long) (effectivePage - 1) * size)
                .stream()
                .map(loan -> toView(loan, now))
                .toList();
        return new Paging<>(rows, effectivePage, size, total, "due_at_asc");
    }

    public Map<String, Object> loan(UUID accountId, String loanId) {
        Loan loan = loans.findOne(accountId, Ids.parse(loanId), Times.utc(clock))
                .orElseThrow(() -> ApiException.notFound("LOAN_NOT_FOUND"));
        return toView(loan, Times.utc(clock));
    }

    @Override
    public Optional<Loan> findOne(UUID accountId, UUID loanId, LocalDateTime now) {
        return loans.findOne(accountId, loanId, now);
    }

    @Override
    public List<Loan> findByAccount(UUID accountId, String state, LocalDateTime now, int limit, long offset) {
        return loans.findByAccount(accountId, state, now, limit, offset);
    }

    @Override
    public long countByAccount(UUID accountId, String state, LocalDateTime now) {
        return loans.countByAccount(accountId, state, now);
    }

    public Map<String, Object> toView(Loan loan, LocalDateTime now) {
        var zone = policies.policy().zone();
        Map<String, Object> data = Maps.of(
                "id", loan.loanId(),
                "catalogItemId", loan.itemId(),
                "title", loan.titleSnapshot(),
                "creator", loan.creatorSnapshot(),
                "type", loan.typeSnapshot(),
                "borrowedAt", atZone(loan.borrowedAt()),
                "dueAt", atZone(loan.dueAt()),
                "returnedAt", loan.returnedAt() == null ? null : atZone(loan.returnedAt()),
                "status", loan.status(),
                "catalogStatus", loan.catalogStatus(),
                "policyVersion", loan.policyVersion());
        if (loan.returnedAt() == null) {
            Instant due = loan.dueAt().toInstant(ZoneOffset.UTC);
            Instant instantNow = now.toInstant(ZoneOffset.UTC);
            if (!instantNow.isBefore(due)) {
                data.put("dayCountType", "OVERDUE_DAYS");
                data.put("days", Math.max(0, ChronoUnit.DAYS.between(
                        due.atZone(zone).toLocalDate(), instantNow.atZone(zone).toLocalDate())));
            } else if (Duration.between(instantNow, due).compareTo(Duration.ofDays(1)) < 0) {
                data.put("dayCountType", "IMMINENT");
                data.put("days", 0);
            } else {
                data.put("dayCountType", "REMAINING_DAYS");
                data.put("days", Math.max(0, ChronoUnit.DAYS.between(
                        instantNow.atZone(zone).toLocalDate(), due.atZone(zone).toLocalDate())));
            }
        }
        return data;
    }

    private String atZone(LocalDateTime utc) {
        return utc.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(policies.policy().zone())
                .toOffsetDateTime()
                .toString();
    }
}
