package com.elibrary.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException e, HttpServletRequest r) {
        return error(e.status(), e.code(), e.getMessage(), Map.of(), r);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
        var details=e.getBindingResult().getFieldErrors().stream().collect(java.util.stream.Collectors.toMap(FieldError::getField, x -> x.getDefaultMessage()==null?"invalid":x.getDefaultMessage(), (a,b)->a));
        return error(400, "INVALID_REQUEST", "Request validation failed", details, r);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<?> malformed(HttpMessageNotReadableException e, HttpServletRequest r) {
        return error(400, "INVALID_REQUEST", "Malformed request body", Map.of(), r);
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class) ResponseEntity<?> invalidParameter(MethodArgumentTypeMismatchException e, HttpServletRequest r) {
        return error(400, "INVALID_REQUEST", "Invalid request parameter", Map.of("parameter", e.getName()), r);
    }
    @ExceptionHandler(DataAccessException.class) ResponseEntity<?> database(DataAccessException e, HttpServletRequest r) {
        log.error("Database request failed; traceId={}", trace(r), e);
        return error(503, "DEPENDENCY_UNAVAILABLE", "A required service is unavailable", Map.of(), r);
    }
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception e, HttpServletRequest r) {
        log.error("Unexpected request failure; traceId={}", trace(r), e);
        return error(500, "INTERNAL_ERROR", "Unexpected error", Map.of(), r);
    }
    private ResponseEntity<?> error(int status, String code, String message, Map<String, ?> details, HttpServletRequest request) {
        return ResponseEntity.status(status).body(Map.of(
                "code", code,
                "message", message,
                "details", details,
                "timestamp", Instant.now(),
                "traceId", trace(request)));
    }

    private String trace(HttpServletRequest r) { Object id=r.getAttribute(TraceFilter.TRACE_ID_ATTRIBUTE); return id==null?"":id.toString(); }
}
