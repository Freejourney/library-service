package com.elibrary.lending.api;

import com.elibrary.common.ApiResponses;
import com.elibrary.common.OperationResult;
import com.elibrary.common.Subjects;
import com.elibrary.lending.application.BorrowLoan;
import com.elibrary.lending.application.ReturnLoan;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 借阅与归还（均需 Idempotency-Key）。 */
@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {

    public record BorrowRequest(@NotBlank @Size(max = 36) String catalogItemId) {}

    private final BorrowLoan borrower;
    private final ReturnLoan returner;

    public LoanController(BorrowLoan borrower, ReturnLoan returner) {
        this.borrower = borrower;
        this.returner = returner;
    }

    @PostMapping
    public ResponseEntity<?> borrow(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody BorrowRequest body,
            HttpServletRequest request) {
        UUID accountId = Subjects.accountId(jwt.getSubject());
        OperationResult result = borrower.borrow(accountId, body.catalogItemId(), key);
        return ApiResponses.operation(result, request);
    }

    @PostMapping("/{id}/return")
    public ResponseEntity<?> returnLoan(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        UUID accountId = Subjects.accountId(jwt.getSubject());
        OperationResult result = returner.returnLoan(accountId, id, key);
        return ApiResponses.operation(result, request);
    }
}