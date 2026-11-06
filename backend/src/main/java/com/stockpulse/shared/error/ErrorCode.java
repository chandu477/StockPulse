package com.stockpulse.shared.error;

import org.springframework.http.HttpStatus;

/** Fixed error-code catalogue (observability §1). The frontend switches on these, never on text. */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
  UNKNOWN_STRATEGY(HttpStatus.BAD_REQUEST),
  PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
  SUGGESTION_NOT_FOUND(HttpStatus.NOT_FOUND),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
  DUPLICATE_SKU(HttpStatus.CONFLICT),
  INSUFFICIENT_STOCK(HttpStatus.CONFLICT),
  NOT_PENDING(HttpStatus.CONFLICT),
  CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
