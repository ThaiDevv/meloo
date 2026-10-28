package com.meloo.shared.api;

import org.springframework.http.HttpStatusCode;

/** Expected API error that is safe to expose to a client. */
public final class ApiException extends RuntimeException {

  private final HttpStatusCode status;
  private final String code;

  public ApiException(HttpStatusCode status, String code, String message) {
    super(message);
    if (code.isBlank()) {
      throw new IllegalArgumentException("code must not be blank");
    }
    this.status = status;
    this.code = code;
  }

  public HttpStatusCode status() {
    return status;
  }

  public String code() {
    return code;
  }
}
