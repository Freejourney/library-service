package com.elibrary.lending.api;

import com.elibrary.common.ApiResponses;
import com.elibrary.common.Subjects;
import com.elibrary.lending.application.IdempotencyQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 幂等操作结果查询（网络结果未知时取回原结果）。 */
@RestController
@RequestMapping("/api/v1/loan-operations")
public class OperationController {

    private final IdempotencyQueryService operations;

    public OperationController(IdempotencyQueryService operations) {
        this.operations = operations;
    }

    @GetMapping("/{idempotencyKey}")
    public ResponseEntity<?> operation(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable String idempotencyKey,
                                       HttpServletRequest request) {
        UUID accountId = Subjects.accountId(jwt.getSubject());
        return ApiResponses.ok(operations.operation(accountId, idempotencyKey), request);
    }
}