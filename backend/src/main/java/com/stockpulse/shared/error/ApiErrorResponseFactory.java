package com.stockpulse.shared.error;

import com.stockpulse.shared.web.TraceIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** The only place that builds ApiErrorResponse instances. */
@Component
public class ApiErrorResponseFactory {
  private final Clock clock;

  public ApiErrorResponseFactory(Clock clock) {
    this.clock = clock;
  }

  public ApiErrorResponse build(
      ErrorCode code, String message, String path, List<FieldErrorItem> errors) {
    return new ApiErrorResponse(
        Instant.now(clock),
        code.status().value(),
        code.status().getReasonPhrase(),
        code.name(),
        message,
        path,
        currentTraceId(),
        errors == null ? List.of() : List.copyOf(errors));
  }

  private static String currentTraceId() {
    String id = MDC.get(TraceIdFilter.MDC_KEY);
    return id == null ? "" : id;
  }
}
