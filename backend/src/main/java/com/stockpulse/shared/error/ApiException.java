package com.stockpulse.shared.error;

import java.util.List;

/** Business error that maps to the standard error response. Message must be safe to show users. */
public class ApiException extends RuntimeException {
  private final ErrorCode code;
  private final transient List<FieldErrorItem> errors;

  public ApiException(ErrorCode code, String message) {
    this(code, message, List.of());
  }

  public ApiException(ErrorCode code, String message, List<FieldErrorItem> errors) {
    super(message);
    this.code = code;
    this.errors = List.copyOf(errors);
  }

  public ErrorCode code() {
    return code;
  }

  public List<FieldErrorItem> errors() {
    return errors;
  }

  public static ApiException productNotFound(String id) {
    return new ApiException(ErrorCode.PRODUCT_NOT_FOUND, "Product " + id + " was not found.");
  }

  public static ApiException suggestionNotFound(long id) {
    return new ApiException(ErrorCode.SUGGESTION_NOT_FOUND, "Suggestion " + id + " was not found.");
  }
}
