package com.meloo.audio.domain;

import org.springframework.http.HttpStatus;

public class AudioRateLimitException extends AudioException {

  private final long retryAfterSeconds;

  public AudioRateLimitException(long retryAfterSeconds) {
    super(
        HttpStatus.TOO_MANY_REQUESTS,
        "RATE_LIMITED",
        "Too Many Requests",
        "Too many audio stream requests for this token. Please wait before retrying.");
    this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
