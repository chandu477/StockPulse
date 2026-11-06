package com.stockpulse.controller;

import com.stockpulse.shared.error.ApiErrorResponse;
import com.stockpulse.shared.error.ApiErrorResponseFactory;
import com.stockpulse.shared.error.ErrorCode;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Replaces Spring's /error so errors raised outside controllers keep the standard shape. */
@RestController
public class ApiErrorController implements ErrorController {
  private final ApiErrorResponseFactory factory;

  public ApiErrorController(ApiErrorResponseFactory factory) {
    this.factory = factory;
  }

  @RequestMapping("/error")
  ResponseEntity<ApiErrorResponse> error(HttpServletRequest req) {
    Object statusAttr = req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int status = statusAttr instanceof Integer i ? i : 500;
    Object uriAttr = req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
    String path = uriAttr == null ? req.getRequestURI() : uriAttr.toString();
    ErrorCode code =
        switch (status) {
          case 400 -> ErrorCode.VALIDATION_FAILED;
          case 404 -> ErrorCode.NOT_FOUND;
          case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
          default -> ErrorCode.INTERNAL_ERROR;
        };
    String message =
        code == ErrorCode.INTERNAL_ERROR
            ? "An unexpected error occurred."
            : HttpStatus.valueOf(code.status().value()).getReasonPhrase();
    return ResponseEntity.status(code.status()).body(factory.build(code, message, path, List.of()));
  }
}
