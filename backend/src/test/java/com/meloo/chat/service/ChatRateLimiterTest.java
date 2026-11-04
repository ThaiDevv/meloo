package com.meloo.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.chat.config.ChatProperties;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatRateLimiterTest {

  private ChatRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    ChatProperties properties =
        new ChatProperties(Duration.ofSeconds(1), 200, Duration.ofMinutes(5), 50);
    rateLimiter = new ChatRateLimiter(properties);
  }

  @Test
  @DisplayName("Allows first message and blocks rapid second message within 1 second (BR-16)")
  void rateLimitPerUser() {
    UUID userA = UUID.randomUUID();

    // First attempt -> allowed
    assertThat(rateLimiter.tryAcquire(userA)).isTrue();

    // Immediate second attempt (< 1s) -> blocked
    assertThat(rateLimiter.tryAcquire(userA)).isFalse();
  }

  @Test
  @DisplayName("Maintains independent rate limits for different users")
  void independentUsers() {
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();

    // User A acquires
    assertThat(rateLimiter.tryAcquire(userA)).isTrue();
    // User A immediately blocked
    assertThat(rateLimiter.tryAcquire(userA)).isFalse();

    // User B is independent, should be allowed
    assertThat(rateLimiter.tryAcquire(userB)).isTrue();
  }

  @Test
  @DisplayName("Handles null user safely")
  void handlesNullUser() {
    assertThat(rateLimiter.tryAcquire(null)).isFalse();
  }
}
