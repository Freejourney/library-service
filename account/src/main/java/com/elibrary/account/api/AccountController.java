package com.elibrary.account.api;

import com.elibrary.account.application.AccountApplicationService;
import com.elibrary.account.application.LoanCountPort;
import com.elibrary.common.LendingPolicyPort;
import com.elibrary.common.ApiResponses;
import com.elibrary.common.Maps;
import com.elibrary.common.Subjects;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** 账号资格查询。账号的创建/冻结/注销由受信任渠道完成，不开放读者 API。 */
@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final AccountApplicationService accounts;
    private final LoanCountPort loanCounts;
    private final LendingPolicyPort lendingPolicy;

    public AccountController(AccountApplicationService accounts, LoanCountPort loanCounts,
                             LendingPolicyPort lendingPolicy) {
        this.accounts = accounts;
        this.loanCounts = loanCounts;
        this.lendingPolicy = lendingPolicy;
    }

    @GetMapping("/accounts/me")
    public ResponseEntity<?> myAccount(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        UUID accountId = Subjects.accountId(jwt.getSubject());
        AccountApplicationService.GetMyAccount account = accounts.getMyAccount(
                accountId,
                loanCounts.activeLoanCount(accountId),
                loanCounts.overdueLoanCount(accountId));
        Map<String, Object> data = Maps.of(
                "accountId", account.accountId(),
                "status", account.status(),
                "eligibilityLevel", account.eligibilityLevel(),
                "displayName", account.displayName(),
                "borrowingEligible", account.borrowingEligible(),
                "eligibilityReasons", account.eligibilityReasons(),
                "activeLoanCount", account.activeLoanCount(),
                "activeLoanLimit", lendingPolicy.loanPolicy().maxActiveLoans());
        return ApiResponses.ok(data, request);
    }
}
