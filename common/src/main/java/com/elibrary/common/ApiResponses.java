package com.elibrary.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

public final class ApiResponses {
    private ApiResponses() {}

    public static ResponseEntity<?> ok(Object data, HttpServletRequest request) {
        return ResponseEntity.ok(ApiEnvelope.ok(data, traceId(request)));
    }

    public static ResponseEntity<?> operation(OperationResult result, HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status());
        if (result.replay()) {
            response.header("Idempotent-Replay", "true");
        }
        return response.body(ApiEnvelope.ok(result.data(), traceId(request)));
    }

    public static String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceFilter.TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}