package com.elibrary.account.domain;

import java.util.UUID;

/** 读者账号。 */
public record ReaderAccount(
        UUID accountId,
        AccountStatus status,
        String eligibilityLevel,
        String displayName) {

    public static ReaderAccount of(UUID accountId, String status, String eligibilityLevel, String displayName) {
        return new ReaderAccount(accountId, AccountStatus.valueOf(status), eligibilityLevel, displayName);
    }

    public boolean eligibleForBorrowing() {
        return status.eligibleForBorrowing();
    }
}