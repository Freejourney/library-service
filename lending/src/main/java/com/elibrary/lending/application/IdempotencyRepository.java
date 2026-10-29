package com.elibrary.lending.application;

import com.elibrary.lending.infrastructure.IdempotencyMapper;
import com.elibrary.lending.infrastructure.IdempotencyRow;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class IdempotencyRepository {

    public record Record(byte[] requestHash, int httpStatus, String responseJson) {}

    private final IdempotencyMapper mapper;

    public IdempotencyRepository(IdempotencyMapper mapper) {
        this.mapper = mapper;
    }

    public Record find(UUID accountId, String operation, String key) {
        IdempotencyRow row = mapper.find(accountId, operation, key);
        return row == null ? null : new Record(row.requestHash(), row.httpStatus(), row.responseJson());
    }

    public void deleteExpired(UUID accountId, String operation, String key) {
        mapper.deleteExpired(accountId, operation, key);
    }

    public void save(UUID accountId, String operation, String key, byte[] requestHash,
                     int status, String responseJson, int retentionDays) {
        mapper.save(accountId, operation, key, requestHash, status, responseJson, retentionDays);
    }

    public Record findLatest(UUID accountId, String key) {
        IdempotencyRow row = mapper.findLatest(accountId, key);
        return row == null ? null : new Record(row.requestHash(), row.httpStatus(), row.responseJson());
    }

    public void deleteAllExpired() {
        mapper.deleteAllExpired();
    }
}