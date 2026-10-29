package com.elibrary.common;

import java.time.Instant;

public record ApiEnvelope<T>(String code, String message, T data, Instant timestamp, String traceId) {
    public static <T> ApiEnvelope<T> ok(T data, String trace) { return new ApiEnvelope<>("OK", "success", data, Instant.now(), trace); }
}
