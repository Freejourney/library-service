package com.elibrary.config;

import com.elibrary.common.LendingPolicyPort;

/** LendingPolicyPort 默认实现：读取 elibrary.loan.* 配置。 */
public class ConfiguredLendingPolicy implements LendingPolicyPort {

    private final LoanProperties properties;

    public ConfiguredLendingPolicy(LoanProperties properties) {
        this.properties = properties;
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
}
