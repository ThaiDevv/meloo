package com.meloo.daily.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Generated interfaces use method validation, which may throw before entering the controller. */
@RestControllerAdvice(assignableTypes = DailyController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DailyValidationHandler {
  @ExceptionHandler(DailyRateLimitException.class)
  public ResponseEntity<ProblemDetail> rateLimited(
      DailyRateLimitException exception, HttpServletRequest request) {
    var problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS, "Daily request rate exceeded");
    problem.setProperty("code", "RATE_LIMITED");
    problem.setProperty(
        "correlationId", request.getAttribute("com.meloo.shared.web.CorrelationIdFilter.id"));
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", Long.toString(exception.retryAfter()))
        .body(problem);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ProblemDetail invalidRequest(HttpServletRequest request) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid daily request");
    problem.setProperty("code", "VALIDATION_FAILED");
    problem.setProperty(
        "correlationId", request.getAttribute("com.meloo.shared.web.CorrelationIdFilter.id"));
    return problem;
  }
}
