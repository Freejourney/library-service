package com.elibrary.account.application;

import com.elibrary.account.domain.ReaderAccount;

import java.util.Optional;
import java.util.UUID;

/** account 模块对外的读取接口，供 lending 等模块调用（不得直接访问对方 Repository）。 */
public interface AccountAccessPort {
    Optional<ReaderAccount> findById(UUID accountId);

    /** 行锁读取，用于借阅事务内串行化同一读者。 */
    Optional<ReaderAccount> lockById(UUID accountId);
}