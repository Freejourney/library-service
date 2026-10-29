package com.elibrary.lending.application;

import com.elibrary.lending.domain.Loan;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 借阅记录读写端口（lending 内部/对外使用）。 */
public interface LoanRepository {

    long countActive(UUID accountId);

    long countOverdue(UUID accountId, LocalDateTime now);

    long countDuplicate(UUID accountId, UUID itemId);

    Optional<Loan> lockById(UUID loanId, UUID accountId);

    void insert(Loan loan);

    boolean markReturned(UUID loanId, String status, LocalDateTime returnedAt);

    Optional<Loan> findOne(UUID accountId, UUID loanId, LocalDateTime now);

    List<Loan> findByAccount(UUID accountId, String state, LocalDateTime now, int limit, long offset);

    long countByAccount(UUID accountId, String state, LocalDateTime now);
}