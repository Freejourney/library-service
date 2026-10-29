package com.elibrary.lending.infrastructure;

import com.elibrary.lending.domain.Loan;

import java.time.LocalDateTime;
import java.util.UUID;

/** loans 表行映射。 */
public record LoanRow(
        UUID loanId,
        UUID accountId,
        UUID itemId,
        String status,
        LocalDateTime borrowedAt,
        LocalDateTime dueAt,
        LocalDateTime returnedAt,
        String titleSnapshot,
        String creatorSnapshot,
        String typeSnapshot,
        String policyVersion,
        String catalogStatus) {

    public Loan toDomain() {
        return new Loan(
                loanId, accountId, itemId, status, borrowedAt, dueAt, returnedAt,
                titleSnapshot, creatorSnapshot, typeSnapshot, policyVersion, catalogStatus);
    }
}