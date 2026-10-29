package com.elibrary.lending.application;

import com.elibrary.common.LendingPolicyPort;
import org.springframework.context.annotation.Primary;
import com.elibrary.config.LoanProperties;
import com.elibrary.lending.domain.LoanPolicy;
import org.springframework.stereotype.Service;

/** 借阅策略：配置 -> 领域 LoanPolicy，并对外提供视图（供 catalog 详情使用）。 */
@Service
@Primary
public class LendingPolicyService implements LendingPolicyPort {

    private final LoanProperties properties;

    public LendingPolicyService(LoanProperties properties) {
        this.properties = properties;
    }

    public LoanPolicy policy() {
        return new LoanPolicy(
                properties.maxActive(),
                properties.days(),
                properties.dueTime(),
                properties.zone(),
                properties.policyVersion());
    }

    @Override
    public LoanPolicyView loanPolicy() {
        return new LoanPolicyView(
                properties.days(),
                properties.dueTime().toString(),
                properties.zone().getId(),
                properties.maxActive(),
                "NOT_SUPPORTED");
    }

    public int idempotencyRetentionDays() {
        return properties.idempotencyRetentionDays();
    }
}
