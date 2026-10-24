package com.meloo.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.identity.config.IdentityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuestRateLimiterTest {

  @Test
  @DisplayName("Allows 5 creations per hour per IP, blocks 6th with retryAfter > 0")
  void rateLimitEnforcesFivePerHour() {
    IdentityProperties properties =
        new IdentityProperties(null, null, new IdentityProperties.RateLimitProperties(5), null);
    GuestRateLimiter rateLimiter = new GuestRateLimiter(properties);
    String ip = "192.168.1.10";

    for (int i = 0; i < 5; i++) {
      GuestRateLimiter.RateLimitResult result = rateLimiter.tryAcquire(ip);
      assertThat(result.allowed()).isTrue();
      assertThat(result.remainingTokens()).isEqualTo(4 - i);
    }

    GuestRateLimiter.RateLimitResult blocked = rateLimiter.tryAcquire(ip);
    assertThat(blocked.allowed()).isFalse();
    assertThat(blocked.retryAfterSeconds()).isGreaterThan(0);

    // Another IP should still be allowed
    GuestRateLimiter.RateLimitResult otherIp = rateLimiter.tryAcquire("10.0.0.1");
    assertThat(otherIp.allowed()).isTrue();
  }
}
