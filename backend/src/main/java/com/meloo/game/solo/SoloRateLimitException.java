package com.meloo.game.solo;

import java.util.Objects;

public class SoloRateLimitException extends RuntimeException {

  private final long retryAfterSeconds;

  public SoloRateLimitException(long retryAfterSeconds) {
    super("Rate limit exceeded. Try again later.");
    this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
  }

  public SoloRateLimitException(long retryAfterSeconds, String message) {
    super(Objects.requireNonNullElse(message, "Rate limit exceeded. Try again later."));
    this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
