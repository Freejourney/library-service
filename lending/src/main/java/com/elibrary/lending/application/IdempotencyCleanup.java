package com.elibrary.lending.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 幂等记录保留期到期清理（默认 7 天）；不删除 Loan。 */
@Service
public class IdempotencyCleanup {

    private final IdempotencyRepository repository;

    public IdempotencyCleanup(IdempotencyRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void cleanup() {
        repository.deleteAllExpired();
    }
}