package com.meloo.game.solo;

/** Thrown when a player exceeds the allowed rate limit for solo game endpoints. */
public class SoloRateLimitException extends RuntimeException {

  private final long retryAfterSeconds;

  public SoloRateLimitException(long retryAfterSeconds) {
    super("Rate limit exceeded. Try again later.");
    this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
