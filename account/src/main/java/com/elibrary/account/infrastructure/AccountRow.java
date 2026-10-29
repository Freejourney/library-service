package com.elibrary.account.infrastructure;

import com.elibrary.account.domain.ReaderAccount;

import java.util.UUID;

/** MyBatis 行映射结果。 */
public record AccountRow(UUID accountId, String status, String eligibilityLevel, String displayName) {
    ReaderAccount toDomain() {
        return ReaderAccount.of(accountId, status, eligibilityLevel, displayName);
    }
}