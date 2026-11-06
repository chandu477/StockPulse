package com.stockpulse.shared.web;

import com.stockpulse.shared.logging.LogEvents;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** One line per API request into stockpulse-access.log. No headers or bodies are logged. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AccessLogFilter extends OncePerRequestFilter {
  private static final Logger access = LoggerFactory.getLogger("stockpulse.access");

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      access
          .atInfo()
          .addKeyValue("event", LogEvents.HTTP_REQUEST)
          .addKeyValue("http.method", request.getMethod())
          .addKeyValue("url.path", request.getRequestURI())
          .addKeyValue("http.status", response.getStatus())
          .addKeyValue("durationMs", ms)
          .log(
              "{} {} {} {}ms",
              request.getMethod(),
              request.getRequestURI(),
              response.getStatus(),
              ms);
    }
  }
}
