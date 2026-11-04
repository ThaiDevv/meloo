package com.meloo.chat.service;

import com.meloo.chat.config.ChatProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** BR-16: Enforces chat rate limit (1 message / second / user). */
@Component
public class ChatRateLimiter {

  private final Duration interval;
  private final ConcurrentHashMap<UUID, Instant> lastMessageTimes = new ConcurrentHashMap<>();

  public ChatRateLimiter(ChatProperties properties) {
    Objects.requireNonNull(properties, "properties");
    this.interval = properties.rateLimitInterval();
  }

  /**
   * Attempts to acquire permission to send a message.
   *
   * @param userId the user attempting to send
   * @return true if permitted, false if rate limited
   */
  public boolean tryAcquire(UUID userId) {
    if (userId == null) {
      return false;
    }
    Instant now = Instant.now();

    // Occasional cleanup of stale entries if map gets large
    if (lastMessageTimes.size() > 500) {
      lastMessageTimes
          .entrySet()
          .removeIf(e -> Duration.between(e.getValue(), now).toSeconds() > 60);
    }

    java.util.concurrent.atomic.AtomicBoolean acquired =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    lastMessageTimes.compute(
        userId,
        (k, last) -> {
          if (last == null || Duration.between(last, now).compareTo(interval) >= 0) {
            acquired.set(true);
            return now;
          }
          return last;
        });

    return acquired.get();
  }

  void reset() {
    lastMessageTimes.clear();
  }
}
