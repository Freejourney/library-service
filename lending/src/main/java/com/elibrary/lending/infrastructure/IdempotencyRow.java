package com.elibrary.lending.infrastructure;

/** idempotency_records 行映射。 */
public record IdempotencyRow(byte[] requestHash, int httpStatus, String responseJson) {}