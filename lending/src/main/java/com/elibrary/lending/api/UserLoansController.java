package com.elibrary.lending.api;

import com.elibrary.common.ApiResponses;
import com.elibrary.common.Subjects;
import com.elibrary.lending.application.LoanQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 个人借阅记录与单条借阅详情（须为本人）。 */
@RestController
@RequestMapping("/api/v1")
public class UserLoansController {

    private final LoanQueryService loanQueries;

    public UserLoansController(LoanQueryService loanQueries) {
        this.loanQueries = loanQueries;
    }

    @GetMapping("/users/{userId}/loans")
    public ResponseEntity<?> myLoans(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            HttpServletRequest request) {
        UUID accountId = Subjects.accountId(jwt.getSubject());
        return ApiResponses.ok(loanQueries.myLoans(accountId, userId, status, page, pageSize), request);
    }

    @GetMapping("/loans/{id}")
    public ResponseEntity<?> loan(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                  HttpServletRequest request) {
        UUID accountId = Subjects.accountId(jwt.getSubject());
        return ApiResponses.ok(loanQueries.loan(accountId, id), request);
    }
}