package com.elibrary.lending.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** 应还时间计算：借阅当天算第 1 日，应还时刻为到期日 23:59（馆区本地）。 */
public final class DueDateCalculator {

    private DueDateCalculator() {}

    public static Instant calculate(Instant borrowedAt, int loanDays, LocalTime dueTime, ZoneId zone) {
        if (loanDays < 1) {
            throw new IllegalArgumentException("loanDays must be positive");
        }
        return LocalDateTime.of(
                        borrowedAt.atZone(zone).toLocalDate().plusDays(loanDays - 1L),
                        dueTime)
                .atZone(zone)
                .toInstant();
    }
}