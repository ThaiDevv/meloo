package com.meloo.game.solo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SoloRateLimiterTest {

  private MutableTestClock clock;
  private SoloProperties.RateLimitProperties properties;
  private SoloRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    clock = new MutableTestClock(Instant.parse("2026-10-31T10:00:00Z"));
    properties =
        new SoloProperties.RateLimitProperties(
            3, 3, Duration.ofMinutes(1), 5, 5, Duration.ofMinutes(1));
    rateLimiter = new SoloRateLimiter(properties, clock);
  }

  @Test
  @DisplayName(
      "Create match allows up to capacity and rejects subsequent requests with 429 Retry-After")
  void createMatchLimit_exceededRequests_throwsException() {
    UUID playerId = UUID.randomUUID();

    // 3 requests allowed
    for (int i = 0; i < 3; i++) {
      assertThatCode(() -> rateLimiter.checkCreateMatch(playerId)).doesNotThrowAnyException();
    }

    // 4th request exceeds burst limit
    assertThatThrownBy(() -> rateLimiter.checkCreateMatch(playerId))
        .isInstanceOf(SoloRateLimitException.class)
        .satisfies(
            ex -> {
              SoloRateLimitException rateLimitEx = (SoloRateLimitException) ex;
              assertThat(rateLimitEx.getRetryAfterSeconds()).isGreaterThanOrEqualTo(1);
            });
  }

  @Test
  @DisplayName("Action rate limiting allows up to actionCapacity and rejects subsequent requests")
  void actionLimit_exceededRequests_throwsException() {
    UUID playerId = UUID.randomUUID();

    // 5 action requests allowed
    for (int i = 0; i < 5; i++) {
      assertThatCode(() -> rateLimiter.checkAction(playerId)).doesNotThrowAnyException();
    }

    // 6th request exceeds action limit
    assertThatThrownBy(() -> rateLimiter.checkAction(playerId))
        .isInstanceOf(SoloRateLimitException.class)
        .satisfies(
            ex -> {
              SoloRateLimitException rateLimitEx = (SoloRateLimitException) ex;
              assertThat(rateLimitEx.getRetryAfterSeconds()).isGreaterThanOrEqualTo(1);
            });
  }

  @Test
  @DisplayName("Rate limits are isolated per player")
  void limitsAreIsolatedPerPlayer() {
    UUID player1 = UUID.randomUUID();
    UUID player2 = UUID.randomUUID();

    // Player 1 consumes all tokens
    for (int i = 0; i < 3; i++) {
      rateLimiter.checkCreateMatch(player1);
    }
    assertThatThrownBy(() -> rateLimiter.checkCreateMatch(player1))
        .isInstanceOf(SoloRateLimitException.class);

    // Player 2 is unaffected
    assertThatCode(() -> rateLimiter.checkCreateMatch(player2)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Tokens refill over time using injected clock")
  void tokensRefillOverTimeWithInjectedClock() {
    UUID playerId = UUID.randomUUID();

    // Consume all 3 create-match tokens
    for (int i = 0; i < 3; i++) {
      rateLimiter.checkCreateMatch(playerId);
    }
    assertThatThrownBy(() -> rateLimiter.checkCreateMatch(playerId))
        .isInstanceOf(SoloRateLimitException.class);

    // Advance clock by 1 minute
    clock.advance(Duration.ofMinutes(1));

    // Tokens refilled, call succeeds
    assertThatCode(() -> rateLimiter.checkCreateMatch(playerId)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Inactive player buckets are evicted by Caffeine after TTL without memory leak")
  void inactivePlayerBuckets_areEvictedAfterTtl() {
    UUID playerId = UUID.randomUUID();

    rateLimiter.checkCreateMatch(playerId);
    rateLimiter.checkAction(playerId);

    assertThat(rateLimiter.getCreateMatchBucketCount()).isGreaterThanOrEqualTo(1);
    assertThat(rateLimiter.getActionBucketCount()).isGreaterThanOrEqualTo(1);

    // Advance clock past 10-minute inactive TTL
    clock.advance(Duration.ofMinutes(15));
    rateLimiter.cleanUp();

    assertThat(rateLimiter.getCreateMatchBucketCount()).isEqualTo(0);
    assertThat(rateLimiter.getActionBucketCount()).isEqualTo(0);
  }

  private static class MutableTestClock extends Clock {
    private Instant now;

    MutableTestClock(Instant initial) {
      this.now = initial;
    }

    void advance(Duration duration) {
      this.now = this.now.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
