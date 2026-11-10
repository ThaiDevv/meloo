package com.meloo.daily.web;

public class DailyRateLimitException extends RuntimeException {
  private final long retryAfter;

  public DailyRateLimitException(long retryAfter) {
    super("Daily request rate exceeded");
    this.retryAfter = retryAfter;
  }

  public long retryAfter() {
    return retryAfter;
  }
}
