package com.elibrary.account.application;

import java.util.UUID;

/** 有效/逾期借阅计数端口，由 lending 模块实现，account 模块不直接访问 loans 表。 */
public interface LoanCountPort {
    long activeLoanCount(UUID accountId);

    long overdueLoanCount(UUID accountId);
}