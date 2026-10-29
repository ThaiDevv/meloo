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
    UUID roundId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();
    int level = 1;

    // 3 requests allowed
    assertThatCode(() -> rateLimiter.checkRateLimit(roundId, playerId, level))
        .doesNotThrowAnyException();
    assertThatCode(() -> rateLimiter.checkRateLimit(roundId, playerId, level))
        .doesNotThrowAnyException();
    assertThatCode(() -> rateLimiter.checkRateLimit(roundId, playerId, level))
        .doesNotThrowAnyException();

    // 4th request exceeds token burst limit
    assertThatThrownBy(() -> rateLimiter.checkRateLimit(roundId, playerId, level))
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
  @DisplayName("token rate limiting is isolated per listening level for the same round and player")
  void tokenRateLimit_isolatedPerLevel() {
    UUID roundId = UUID.randomUUID();
    UUID playerId = UUID.randomUUID();

    // Consume 3 for Level 1
    for (int i = 0; i < 3; i++) {
      rateLimiter.checkRateLimit(roundId, playerId, 1);
    }
    // 4th for Level 1 fails
    assertThatThrownBy(() -> rateLimiter.checkRateLimit(roundId, playerId, 1))
        .isInstanceOf(AudioRateLimitException.class);

    // But Level 2 has its own token bucket (still allows request since player limit is 10)
    assertThatCode(() -> rateLimiter.checkRateLimit(roundId, playerId, 2))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("player rate limiting aggregates across different rounds and levels")
  void playerRateLimit_exceededPlayerRequests_throws429() {
    UUID playerId = UUID.randomUUID();

    // Each round/level allows 3, but player limit is 10
    for (int i = 0; i < 10; i++) {
      UUID roundId = UUID.randomUUID();
      int level = (i % 5) + 1;
      assertThatCode(() -> rateLimiter.checkRateLimit(roundId, playerId, level))
          .doesNotThrowAnyException();
    }

    // 11th request exceeds player limit
    assertThatThrownBy(() -> rateLimiter.checkRateLimit(UUID.randomUUID(), playerId, 1))
        .isInstanceOf(AudioRateLimitException.class)
        .satisfies(
            ex -> {
              AudioRateLimitException rateLimitEx = (AudioRateLimitException) ex;
              assertThat(rateLimitEx.getStatus().value()).isEqualTo(429);
              assertThat(rateLimitEx.getCode()).isEqualTo("RATE_LIMITED");
            });
  }
}
