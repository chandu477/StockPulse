package com.stockpulse.shared.error;

import java.time.Instant;
import java.util.List;

/** The one standard error shape (ADR-9, observability §1). */
public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String code,
    String message,
    String path,
    String traceId,
    List<FieldErrorItem> errors) {}
