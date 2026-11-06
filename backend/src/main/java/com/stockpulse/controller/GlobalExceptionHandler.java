package com.stockpulse.controller;

import com.stockpulse.shared.error.ApiErrorResponse;
import com.stockpulse.shared.error.ApiErrorResponseFactory;
import com.stockpulse.shared.error.ApiException;
import com.stockpulse.shared.error.ErrorCode;
import com.stockpulse.shared.error.FieldErrorItem;
import com.stockpulse.shared.logging.LogEvents;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps every exception to the standard error shape. Never exposes stack traces or internals. */
@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final ApiErrorResponseFactory factory;

  public GlobalExceptionHandler(ApiErrorResponseFactory factory) {
    this.factory = factory;
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
    return respond(ex.code(), ex.getMessage(), req, ex.errors());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> handleInvalid(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    List<FieldErrorItem> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(f -> new FieldErrorItem(f.getField(), f.getDefaultMessage(), f.getRejectedValue()))
            .toList();
    return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed.", req, errors);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiErrorResponse> handleConstraint(
      ConstraintViolationException ex, HttpServletRequest req) {
    List<FieldErrorItem> errors =
        ex.getConstraintViolations().stream()
            .map(v ->
                new FieldErrorItem(
                    v.getPropertyPath().toString(), v.getMessage(), v.getInvalidValue()))
            .toList();
    return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed.", req, errors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiErrorResponse> handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest req) {
    return respond(
        ErrorCode.VALIDATION_FAILED,
        "Request body is missing, malformed, or has unknown or invalid fields.",
        req,
        List.of());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
    return respond(
        ErrorCode.VALIDATION_FAILED,
        "Invalid value for parameter '" + ex.getName() + "'.",
        req,
        List.of(new FieldErrorItem(ex.getName(), "invalid value", String.valueOf(ex.getValue()))));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<ApiErrorResponse> handleMissingParam(
      MissingServletRequestParameterException ex, HttpServletRequest req) {
    return respond(
        ErrorCode.VALIDATION_FAILED,
        "Missing parameter '" + ex.getParameterName() + "'.",
        req,
        List.of());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiErrorResponse> handleMediaType(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
    return respond(
        ErrorCode.VALIDATION_FAILED, "Content-Type must be application/json.", req, List.of());
  }

  @ExceptionHandler({
    OptimisticLockingFailureException.class,
    ObjectOptimisticLockingFailureException.class
  })
  ResponseEntity<ApiErrorResponse> handleOptimisticLock(Exception ex, HttpServletRequest req) {
    return respond(
        ErrorCode.CONCURRENT_MODIFICATION,
        "The resource was changed by another request. Refresh and try again.",
        req,
        List.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiErrorResponse> handleNoResource(
      NoResourceFoundException ex, HttpServletRequest req) {
    return respond(ErrorCode.NOT_FOUND, "No endpoint matches this path.", req, List.of());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiErrorResponse> handleMethod(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
    return respond(
        ErrorCode.METHOD_NOT_ALLOWED,
        "Method " + ex.getMethod() + " is not supported here.",
        req,
        List.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
    log.atError()
        .addKeyValue("event", LogEvents.REQUEST_FAILED)
        .setCause(ex)
        .log("Unhandled error on {} {}", req.getMethod(), req.getRequestURI());
    return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", req, List.of());
  }

  private ResponseEntity<ApiErrorResponse> respond(
      ErrorCode code, String message, HttpServletRequest req, List<FieldErrorItem> errors) {
    if (code != ErrorCode.INTERNAL_ERROR) {
      log.atWarn()
          .addKeyValue("event", LogEvents.REQUEST_FAILED)
          .addKeyValue("code", code.name())
          .log("{} {} -> {} {}", req.getMethod(), req.getRequestURI(), code.status().value(), code);
    }
    return ResponseEntity.status(code.status())
        .body(factory.build(code, message, req.getRequestURI(), errors));
  }
}
