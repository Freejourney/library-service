package com.elibrary.account.infrastructure;

import com.elibrary.account.application.AccountAccessPort;
import com.elibrary.account.domain.ReaderAccount;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class AccountRepository implements AccountAccessPort {

    private final AccountMapper mapper;

    public AccountRepository(AccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ReaderAccount> findById(UUID accountId) {
        return Optional.ofNullable(mapper.findById(accountId)).map(AccountRow::toDomain);
    }

    @Override
    public Optional<ReaderAccount> lockById(UUID accountId) {
        return Optional.ofNullable(mapper.lockById(accountId)).map(AccountRow::toDomain);
    }
}