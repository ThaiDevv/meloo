package com.meloo.audio.domain;

import org.springframework.http.HttpStatusCode;

public class AudioException extends RuntimeException {

  private final HttpStatusCode status;
  private final String code;
  private final String title;

  public AudioException(HttpStatusCode status, String code, String title, String detail) {
    super(detail);
    this.status = status;
    this.code = code;
    this.title = title;
  }

  public AudioException(
      HttpStatusCode status, String code, String title, String detail, Throwable cause) {
    super(detail, cause);
    this.status = status;
    this.code = code;
    this.title = title;
  }

  public HttpStatusCode getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public String getTitle() {
    return title;
  }
}
