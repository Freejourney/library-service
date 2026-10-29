package com.elibrary.lending.application;

import com.elibrary.common.ApiException;
import com.elibrary.common.OperationResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** 幂等操作服务：借阅/归还共用的幂等键校验、请求摘要比对与结果存取。 */
@Service
public class IdempotencyService {

    private final IdempotencyRepository repository;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public IdempotencyService(IdempotencyRepository repository, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    public void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ApiException(400, "INVALID_IDEMPOTENCY_KEY", "Invalid Idempotency-Key");
        }
    }

    public void deleteExpired(UUID accountId, String operation, String key) {
        repository.deleteExpired(accountId, operation, key);
    }

    public OperationResult lookup(UUID accountId, String operation, String key, byte[] requestHash) {
        IdempotencyRepository.Record record = repository.find(accountId, operation, key);
        if (record == null) {
            return null;
        }
        if (!java.security.MessageDigest.isEqual(requestHash, record.requestHash())) {
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED");
        }
        try {
            return new OperationResult(record.httpStatus(), json.readValue(record.responseJson(), Object.class), true);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void save(UUID accountId, String operation, String key, byte[] requestHash,
                     int status, Object data, int retentionDays) {
        try {
            repository.save(accountId, operation, key, requestHash, status,
                    json.writeValueAsString(data), retentionDays);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public IdempotencyRepository.Record findLatest(UUID accountId, String key) {
        return repository.findLatest(accountId, key);
    }

    public Object readResult(String responseJson) {
        try {
            return json.readValue(responseJson, Object.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}