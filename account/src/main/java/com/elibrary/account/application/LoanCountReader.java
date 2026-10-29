package com.elibrary.account.application;

import com.elibrary.account.infrastructure.LoanCountMapper;
import com.elibrary.common.Times;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/** LoanCountPort 的默认实现：account 服务独立运行时基于 loans 表计数。 */
@Service
@Primary
public class LoanCountReader implements LoanCountPort {

    private final LoanCountMapper mapper;
    private final Clock clock;

    public LoanCountReader(LoanCountMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public long activeLoanCount(UUID accountId) {
        return mapper.countActive(accountId);
    }

    @Override
    public long overdueLoanCount(UUID accountId) {
        return mapper.countOverdue(accountId, Times.utc(clock));
    }
}
