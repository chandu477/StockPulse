package com.stockpulse.integration.llm;

/** Why an LLM call did not produce usable text. Mapped 1:1 to FallbackReason by the AI layer. */
public enum LlmFailure {
  NOT_CONFIGURED,
  TIMEOUT,
  RATE_LIMITED,
  PROVIDER_ERROR,
  EMPTY_RESPONSE,
  CIRCUIT_OPEN,
  BUDGET_EXCEEDED
}
