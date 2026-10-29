package com.elibrary.lending.application;

import com.elibrary.common.ApiException;
import com.elibrary.common.Maps;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/** 幂等操作结果查询（网络结果未知时取回原结果）。 */
@Service
public class IdempotencyQueryService {

    private final IdempotencyService idempotency;

    public IdempotencyQueryService(IdempotencyService idempotency) {
        this.idempotency = idempotency;
    }

    public Map<String, Object> operation(UUID accountId, String key) {
        idempotency.validateKey(key);
        IdempotencyRepository.Record record = idempotency.findLatest(accountId, key);
        if (record == null) {
            throw ApiException.notFound("OPERATION_NOT_FOUND");
        }
        return Maps.of(
                "state", "COMPLETED",
                "httpStatus", record.httpStatus(),
                "result", idempotency.readResult(record.responseJson()));
    }
}
