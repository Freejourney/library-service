package com.elibrary.lending.domain;

import java.time.LocalTime;
import java.time.ZoneId;

/** 借阅策略（配置化）。 */
public record LoanPolicy(
        int maxActive,
        int days,
        LocalTime dueTime,
        ZoneId zone,
        String policyVersion) {

    public LoanPolicy {
        if (maxActive < 1) {
            throw new IllegalArgumentException("maxActive must be positive");
        }
        if (days < 1) {
            throw new IllegalArgumentException("days must be positive");
        }
    }
}