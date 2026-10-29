package com.meloo.audio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meloo.audio.config.AudioProperties;
import com.meloo.audio.domain.AudioRateLimitException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AudioRateLimiterTest {

  private AudioRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    AudioProperties.RateLimitProperties properties =
        new AudioProperties.RateLimitProperties(
            3, 3, Duration.ofMinutes(1), 10, 10, Duration.ofMinutes(1));
    rateLimiter = new AudioRateLimiter(properties);
  }

  @Test
  @DisplayName("token rate limiting allows up to capacity and rejects subsequent requests with 429")
  void tokenRateLimit_exceededRequests_throws429() {
    String token = "sample-token-1234567890";
    UUID playerId = UUID.randomUUID();

    // 3 requests allowed
    assertThatCode(() -> rateLimiter.checkRateLimit(token, playerId)).doesNotThrowAnyException();
    assertThatCode(() -> rateLimiter.checkRateLimit(token, playerId)).doesNotThrowAnyException();
    assertThatCode(() -> rateLimiter.checkRateLimit(token, playerId)).doesNotThrowAnyException();

    // 4th request exceeds token burst limit
    assertThatThrownBy(() -> rateLimiter.checkRateLimit(token, playerId))
        .isInstanceOf(AudioRateLimitException.class)
        .satisfies(
            ex -> {
              AudioRateLimitException rateLimitEx = (AudioRateLimitException) ex;
              assertThat(rateLimitEx.getStatus().value()).isEqualTo(429);
              assertThat(rateLimitEx.getCode()).isEqualTo("RATE_LIMITED");
              assertThat(rateLimitEx.getRetryAfterSeconds()).isGreaterThanOrEqualTo(1);
            });
  }

  @Test
  @DisplayName("player rate limiting aggregates across different tokens")
  void playerRateLimit_exceededPlayerRequests_throws429() {
    UUID playerId = UUID.randomUUID();

    // Each token allows 3, but player limit is 10
    for (int i = 0; i < 10; i++) {
      String token = "token-" + (i / 2) + "-unique-" + i;
      assertThatCode(() -> rateLimiter.checkRateLimit(token, playerId)).doesNotThrowAnyException();
    }

    // 11th request exceeds player limit
    assertThatThrownBy(() -> rateLimiter.checkRateLimit("token-fresh-11", playerId))
        .isInstanceOf(AudioRateLimitException.class)
        .satisfies(
            ex -> {
              AudioRateLimitException rateLimitEx = (AudioRateLimitException) ex;
              assertThat(rateLimitEx.getStatus().value()).isEqualTo(429);
              assertThat(rateLimitEx.getCode()).isEqualTo("RATE_LIMITED");
            });
  }
}
