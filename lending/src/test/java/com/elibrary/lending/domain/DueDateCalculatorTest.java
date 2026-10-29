package com.elibrary.lending.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DueDateCalculatorTest {
    private static final LocalTime DUE_TIME = LocalTime.of(23, 59);

    @Test
    void countsBorrowDayAsFirstLocalDay() {
        Instant borrowedAt = Instant.parse("2026-10-29T02:00:00Z");

        assertEquals(
                Instant.parse("2026-11-11T15:59:00Z"),
                DueDateCalculator.calculate(borrowedAt, 14, DUE_TIME, ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void calculatesAcrossLeapDay() {
        Instant borrowedAt = Instant.parse("2024-02-20T04:00:00Z");

        assertEquals(
                Instant.parse("2024-03-04T15:59:00Z"),
                DueDateCalculator.calculate(borrowedAt, 14, DUE_TIME, ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void calculatesUsingCalendarDaysAcrossDaylightSavingTransition() {
        Instant borrowedAt = Instant.parse("2026-03-07T15:00:00Z");

        assertEquals(
                Instant.parse("2026-03-21T03:59:00Z"),
                DueDateCalculator.calculate(borrowedAt, 14, DUE_TIME, ZoneId.of("America/New_York")));
    }
}