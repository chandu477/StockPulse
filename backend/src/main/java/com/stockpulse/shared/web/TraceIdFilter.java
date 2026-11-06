package com.stockpulse.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Assigns a traceId per request (from traceparent / X-Trace-Id or new), in MDC and response. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
  public static final String MDC_KEY = "traceId";
  public static final String HEADER = "X-Trace-Id";
  private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String traceId = resolve(request);
    MDC.put(MDC_KEY, traceId);
    response.setHeader(HEADER, traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  private static String resolve(HttpServletRequest request) {
    String traceparent = request.getHeader("traceparent");
    if (traceparent != null) {
      String[] parts = traceparent.split("-");
      if (parts.length >= 2 && SAFE.matcher(parts[1]).matches()) {
        return parts[1];
      }
    }
    String header = request.getHeader(HEADER);
    if (header != null && SAFE.matcher(header).matches()) {
      return header;
    }
    return newTraceId();
  }

  public static String newTraceId() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
