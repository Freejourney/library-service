package com.elibrary.common;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 数据库统一存 UTC DATETIME(6) 的时间换算。 */
public final class Times {
    private Times() {}

    public static LocalDateTime utc(Clock clock) {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    public static LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}