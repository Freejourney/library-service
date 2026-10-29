package com.elibrary.common;

/** 借阅规则视图端口，供 catalog 详情展示，由 lending 模块实现。 */
public interface LendingPolicyPort {

    LoanPolicyView loanPolicy();

    record LoanPolicyView(int loanDays, String dueTime, String timeZone,
                          int maxActiveLoans, String renewal) {}
}