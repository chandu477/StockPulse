package com.stockpulse.integration.llm;

public class LlmException extends RuntimeException {
  private final LlmFailure failure;

  public LlmException(LlmFailure failure, String message) {
    super(message);
    this.failure = failure;
  }

  public LlmException(LlmFailure failure, String message, Throwable cause) {
    super(message, cause);
    this.failure = failure;
  }

  public LlmFailure failure() {
    return failure;
  }
}
