package com.elibrary.lending.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/** 借阅记录（含快照字段）。 */
public record Loan(
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

    public boolean returned() {
        return returnedAt != null;
    }

    /** 逾期状态为读时派生：未归还且已过应还时间。 */
    public boolean overdue(LocalDateTime now) {
        return !returned() && !dueAt.isAfter(now);
    }
}