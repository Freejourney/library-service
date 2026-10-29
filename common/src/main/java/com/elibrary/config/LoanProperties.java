package com.elibrary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.time.ZoneId;

@Validated
@ConfigurationProperties("elibrary.loan")
public record LoanProperties(
        @Min(1) int maxActive,
        @Min(1) int days,
        @NotNull LocalTime dueTime,
        @NotNull ZoneId zone,
        @NotBlank String policyVersion,
        @Min(1) int idempotencyRetentionDays) {}
