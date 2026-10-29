package com.elibrary.lending.application;

import com.elibrary.lending.domain.Loan;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 个人借阅记录查询（lending 应用层对外只读接口）。 */
public interface LoanQueryPort {

    Optional<Loan> findOne(UUID accountId, UUID loanId, LocalDateTime now);

    List<Loan> findByAccount(UUID accountId, String state, LocalDateTime now, int limit, long offset);

    long countByAccount(UUID accountId, String state, LocalDateTime now);
}