package com.elibrary.account.application;

import com.elibrary.account.domain.ReaderAccount;
import com.elibrary.common.ApiException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** 账号状态与借阅资格查询。 */
@Service
public class AccountApplicationService {

    private final AccountAccessPort accounts;

    public AccountApplicationService(AccountAccessPort accounts) {
        this.accounts = accounts;
    }

    public GetMyAccount getMyAccount(UUID accountId, long activeLoanCount, long overdueCount) {
        ReaderAccount account = accounts.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND"));
        boolean eligible = account.eligibleForBorrowing() && overdueCount == 0;
        List<String> reasons = new java.util.ArrayList<>();
        if (!account.eligibleForBorrowing()) {
            reasons.add(account.status().name());
        }
        if (overdueCount > 0) {
            reasons.add("OVERDUE_LOANS_EXIST");
        }
        return new GetMyAccount(
                account.accountId(),
                account.status().name(),
                account.eligibilityLevel(),
                account.displayName(),
                eligible,
                reasons,
                activeLoanCount);
    }

    public record GetMyAccount(
            UUID accountId,
            String status,
            String eligibilityLevel,
            String displayName,
            boolean borrowingEligible,
            List<String> eligibilityReasons,
            long activeLoanCount) {}
}
