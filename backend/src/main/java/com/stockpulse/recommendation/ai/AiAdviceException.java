package com.stockpulse.recommendation.ai;

import com.stockpulse.entity.FallbackReason;

/** The AI path failed; the decorator falls back to rules and records this reason (ADR-4). */
public class AiAdviceException extends RuntimeException {
  private final FallbackReason reason;

  public AiAdviceException(FallbackReason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public AiAdviceException(FallbackReason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public FallbackReason reason() {
    return reason;
  }
}
