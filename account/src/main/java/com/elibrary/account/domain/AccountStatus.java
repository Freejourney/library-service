package com.elibrary.account.domain;

/** 账号生命周期状态。 */
public enum AccountStatus {
    NORMAL,
    FROZEN,
    PENDING_VERIFICATION,
    DELETED;

    public boolean eligibleForBorrowing() {
        return this == NORMAL;
    }
}