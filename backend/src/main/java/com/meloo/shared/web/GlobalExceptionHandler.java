package com.meloo.shared.web;

import com.meloo.shared.api.ApiException;
import com.meloo.shared.api.ProblemConstants;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public final class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ProblemDetail handleApiException(ApiException exception, HttpServletRequest request) {
    return problem(
        exception.status().value(),
        safeTitle(exception.status()),
        exception.getMessage(),
        exception.code(),
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
        ProblemConstants.CODE_INTERNAL_ERROR,
        request);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(status, "One or more fields are invalid.");
    problem.setType(URI.create("about:blank"));
    problem.setTitle("Validation failed");
    List<Map<String, String>> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    Map.of(
                        "field",
                        fe.getField(),
                        "message",
                        Objects.requireNonNullElse(fe.getDefaultMessage(), "invalid value")))
            .toList();
    problem.setProperty(ProblemConstants.PROPERTY_ERRORS, fieldErrors);
    problem.setProperty(ProblemConstants.PROPERTY_CODE, ProblemConstants.CODE_VALIDATION_FAILED);
    return createResponseEntity(problem, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(status, "One or more fields are invalid.");
    problem.setType(URI.create("about:blank"));
    problem.setTitle("Validation failed");
    problem.setProperty(ProblemConstants.PROPERTY_CODE, ProblemConstants.CODE_VALIDATION_FAILED);
    return createResponseEntity(problem, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> createResponseEntity(
      Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    ProblemDetail detail =
        body instanceof ProblemDetail problem
            ? problem
            : ProblemDetail.forStatusAndDetail(statusCode, safeDetail(statusCode));
    if (!"Validation failed".equals(detail.getTitle())) {
      detail.setTitle(safeTitle(statusCode));
    }
    if (!"One or more fields are invalid.".equals(detail.getDetail())) {
      detail.setDetail(safeDetail(statusCode));
    }
    if (detail.getType() == null) {
      detail.setType(URI.create("about:blank"));
    }
    if (request instanceof ServletWebRequest servletRequest) {
      HttpServletRequest httpRequest = servletRequest.getRequest();
      detail.setInstance(URI.create(httpRequest.getRequestURI()));
      if (detail.getProperties() == null
          || !detail.getProperties().containsKey(ProblemConstants.PROPERTY_CODE)) {
        detail.setProperty(ProblemConstants.PROPERTY_CODE, defaultCode(statusCode));
      }
      detail.setProperty(ProblemConstants.PROPERTY_CORRELATION_ID, correlationId(httpRequest));
    }
    return super.createResponseEntity(detail, headers, statusCode, request);
  }

  private static ProblemDetail problem(
      int status, String title, String detail, String code, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    problem.setType(URI.create("about:blank"));
    problem.setTitle(title);
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty(ProblemConstants.PROPERTY_CODE, code);
    problem.setProperty(ProblemConstants.PROPERTY_CORRELATION_ID, correlationId(request));
    return problem;
  }

  private static String defaultCode(HttpStatusCode status) {
    if (status instanceof HttpStatus httpStatus) {
      return switch (httpStatus) {
        case BAD_REQUEST -> ProblemConstants.CODE_VALIDATION_FAILED;
        case UNAUTHORIZED -> ProblemConstants.CODE_UNAUTHORIZED;
        case FORBIDDEN -> ProblemConstants.CODE_FORBIDDEN;
        case NOT_FOUND -> ProblemConstants.CODE_NOT_FOUND;
        case METHOD_NOT_ALLOWED -> ProblemConstants.CODE_METHOD_NOT_ALLOWED;
        case UNSUPPORTED_MEDIA_TYPE -> ProblemConstants.CODE_UNSUPPORTED_MEDIA_TYPE;
        case INTERNAL_SERVER_ERROR -> ProblemConstants.CODE_INTERNAL_ERROR;
        default -> httpStatus.name();
      };
    }
    return ProblemConstants.CODE_INTERNAL_ERROR;
  }

  private static String safeTitle(HttpStatusCode status) {
    if (status instanceof HttpStatus httpStatus) {
      return switch (httpStatus) {
        case BAD_REQUEST -> "Invalid request";
        case UNAUTHORIZED -> "Unauthorized";
        case FORBIDDEN -> "Forbidden";
        case NOT_FOUND -> "Resource not found";
        case METHOD_NOT_ALLOWED -> "Method not allowed";
        case CONFLICT -> "Conflict";
        case UNSUPPORTED_MEDIA_TYPE -> "Unsupported media type";
        case INTERNAL_SERVER_ERROR -> "Internal server error";
        default -> httpStatus.getReasonPhrase();
      };
    }
    return "Request failed";
  }

  private static String safeDetail(HttpStatusCode status) {
    if (status.value() == HttpStatus.BAD_REQUEST.value()) return "The request is invalid";
    if (status.value() == HttpStatus.UNAUTHORIZED.value())
      return "The access token is missing or expired.";
    if (status.value() == HttpStatus.FORBIDDEN.value()) return "Access is denied";
    if (status.value() == HttpStatus.NOT_FOUND.value())
      return "The requested resource was not found";
    if (status.value() == HttpStatus.METHOD_NOT_ALLOWED.value())
      return "The HTTP method is not supported";
    if (status.value() == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()) {
      return "The request media type is not supported";
    }
    if (status.value() == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
      return "An unexpected error occurred";
    }
    return "The request could not be completed";
  }

  private static String correlationId(HttpServletRequest request) {
    Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
    return value == null ? "unavailable" : value.toString();
  }
}
