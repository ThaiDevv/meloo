package com.meloo.shared.web;

import com.meloo.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public final class GlobalExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ProblemDetail handleApiException(ApiException exception, HttpServletRequest request) {
    return problem(
        exception.status().value(),
        "Request rejected",
        exception.getMessage(),
        exception.errorCode(),
        request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    ProblemDetail detail =
        problem(
            HttpStatus.BAD_REQUEST.value(),
            "Invalid request",
            "Request validation failed",
            "VALIDATION_FAILED",
            request);
    List<String> violations =
        exception.getBindingResult().getFieldErrors().stream()
            .map(GlobalExceptionHandler::formatViolation)
            .toList();
    detail.setProperty("violations", violations);
    return detail;
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ProblemDetail handleNotFound(NoResourceFoundException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.NOT_FOUND.value(),
        "Resource not found",
        "The requested resource does not exist",
        "RESOURCE_NOT_FOUND",
        request);
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
    String correlationId = correlationId(request);
    LOGGER.error(
        "Unhandled request failure, correlationId={}, exceptionType={}",
        correlationId,
        exception.getClass().getName());
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        "Internal server error",
        "An unexpected error occurred",
        "INTERNAL_ERROR",
        request);
  }

  private static ProblemDetail problem(
      int status, String title, String detail, String errorCode, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    problem.setTitle(title);
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("errorCode", errorCode);
    problem.setProperty("correlationId", correlationId(request));
    return problem;
  }

  private static String formatViolation(FieldError error) {
    return error.getField() + ": " + error.getDefaultMessage();
  }

  private static String correlationId(HttpServletRequest request) {
    Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
    return value == null ? "unavailable" : value.toString();
  }
}
