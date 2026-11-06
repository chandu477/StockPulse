package com.stockpulse.integration.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** LLM settings (ADR-10). The key comes only from the LLM_API_KEY environment variable. */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
    String provider,
    String model,
    String baseUrl,
    String apiKey,
    Timeout timeout,
    int maxCallsPerMinute,
    Breaker breaker) {

  public record Timeout(int connectMs, int readMs) {}

  public record Breaker(int failureRateThreshold, int slidingWindowSize, int openSeconds) {}

  public boolean hasKey() {
    return apiKey != null && !apiKey.isBlank();
  }

  public boolean hasModel() {
    return model != null && !model.isBlank();
  }

  /** Masked for logs: never print the key itself. */
  @Override
  public String toString() {
    return "LlmProperties[provider=%s, model=%s, baseUrl=%s, apiKey=%s]"
        .formatted(provider, model, baseUrl, hasKey() ? "***" : "<none>");
  }
}
