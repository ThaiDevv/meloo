package com.meloo.shared.web;

import com.meloo.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public final class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

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

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request)
      throws AccessDeniedException, AuthenticationException {
    if (exception instanceof AccessDeniedException denied) {
      throw denied;
    }
    if (exception instanceof AuthenticationException unauthenticated) {
      throw unauthenticated;
    }
    String correlationId = correlationId(request);
    // Do not log exception messages: domain and upstream failures can contain protected answers.
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

  @Override
  protected ResponseEntity<Object> createResponseEntity(
      Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    ProblemDetail detail =
        body instanceof ProblemDetail problem
            ? problem
            : ProblemDetail.forStatusAndDetail(statusCode, safeDetail(statusCode));
    detail.setTitle(safeTitle(statusCode));
    detail.setDetail(safeDetail(statusCode));
    if (request instanceof ServletWebRequest servletRequest) {
      detail.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
      detail.setProperty("errorCode", "HTTP_" + statusCode.value());
      detail.setProperty("correlationId", correlationId(servletRequest.getRequest()));
    }
    return super.createResponseEntity(detail, headers, statusCode, request);
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

  private static String safeTitle(HttpStatusCode status) {
    if (status instanceof HttpStatus httpStatus) {
      return switch (httpStatus) {
        case BAD_REQUEST -> "Invalid request";
        case NOT_FOUND -> "Resource not found";
        case METHOD_NOT_ALLOWED -> "Method not allowed";
        case UNSUPPORTED_MEDIA_TYPE -> "Unsupported media type";
        default -> "Request failed";
      };
    }
    return "Request failed";
  }

  private static String safeDetail(HttpStatusCode status) {
    if (status.value() == HttpStatus.BAD_REQUEST.value()) return "The request is invalid";
    if (status.value() == HttpStatus.NOT_FOUND.value())
      return "The requested resource was not found";
    if (status.value() == HttpStatus.METHOD_NOT_ALLOWED.value())
      return "The HTTP method is not supported";
    if (status.value() == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()) {
      return "The request media type is not supported";
    }
    return "The request could not be completed";
  }

  private static String correlationId(HttpServletRequest request) {
    Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
    return value == null ? "unavailable" : value.toString();
  }
}
