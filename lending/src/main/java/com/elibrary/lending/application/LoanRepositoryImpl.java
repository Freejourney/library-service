package com.elibrary.lending.application;

import com.elibrary.lending.domain.Loan;
import com.elibrary.lending.infrastructure.LoanMapper;
import com.elibrary.lending.infrastructure.LoanRow;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LoanRepositoryImpl implements LoanRepository {

    private final LoanMapper mapper;

    public LoanRepositoryImpl(LoanMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long countActive(UUID accountId) {
        return mapper.countActive(accountId);
    }

    @Override
    public long countOverdue(UUID accountId, LocalDateTime now) {
        return mapper.countOverdue(accountId, now);
    }

    @Override
    public long countDuplicate(UUID accountId, UUID itemId) {
        return mapper.countDuplicate(accountId, itemId);
    }

    @Override
    public Optional<Loan> lockById(UUID loanId, UUID accountId) {
        return Optional.ofNullable(mapper.lockById(loanId, accountId)).map(LoanRow::toDomain);
    }

    @Override
    public void insert(Loan loan) {
        mapper.insert(loan);
    }

    @Override
    public boolean markReturned(UUID loanId, String status, LocalDateTime returnedAt) {
        return mapper.markReturned(loanId, status, returnedAt) == 1;
    }

    @Override
    public Optional<Loan> findOne(UUID accountId, UUID loanId, LocalDateTime now) {
        return Optional.ofNullable(mapper.findOne(accountId, loanId, now)).map(LoanRow::toDomain);
    }

    @Override
    public List<Loan> findByAccount(UUID accountId, String state, LocalDateTime now, int limit, long offset) {
        return mapper.findByAccount(accountId, state, now, limit, offset).stream().map(LoanRow::toDomain).toList();
    }

    @Override
    public long countByAccount(UUID accountId, String state, LocalDateTime now) {
        return mapper.countByAccount(accountId, state, now);
    }
}