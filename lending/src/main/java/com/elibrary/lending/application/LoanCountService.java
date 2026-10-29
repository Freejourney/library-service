package com.elibrary.lending.application;

import com.elibrary.account.application.LoanCountPort;
import com.elibrary.common.Times;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/** 账号资格展示所需的借阅计数，实现 account 模块声明的 LoanCountPort。 */
@Service
@Primary
public class LoanCountService implements LoanCountPort {

    private final LoanRepository loans;
    private final Clock clock;

    public LoanCountService(LoanRepository loans, Clock clock) {
        this.loans = loans;
        this.clock = clock;
    }

    @Override
    public long activeLoanCount(UUID accountId) {
        return loans.countActive(accountId);
    }

    @Override
    public long overdueLoanCount(UUID accountId) {
        return loans.countOverdue(accountId, Times.utc(clock));
    }
}