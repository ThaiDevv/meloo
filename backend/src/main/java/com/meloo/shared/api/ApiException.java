package com.meloo.shared.api;

import org.springframework.http.HttpStatusCode;

/** Expected API error that is safe to expose to a client. */
public final class ApiException extends RuntimeException {

  private final HttpStatusCode status;
  private final String errorCode;

  public ApiException(HttpStatusCode status, String errorCode, String message) {
    super(message);
    if (errorCode.isBlank()) {
      throw new IllegalArgumentException("errorCode must not be blank");
    }
    this.status = status;
    this.errorCode = errorCode;
  }

  public HttpStatusCode status() {
    return status;
  }

  public String errorCode() {
    return errorCode;
  }
}
