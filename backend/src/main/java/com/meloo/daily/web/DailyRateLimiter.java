package com.meloo.daily.web;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.meloo.daily.service.DailyProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Bounds daily endpoint work per player; audio replay has its own limiter in the audio module. */
@Component
public class DailyRateLimiter {
  private final LoadingCache<UUID, Bucket> buckets;

  public DailyRateLimiter(DailyProperties properties) {
    buckets =
        Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build(
                id ->
                    Bucket.builder()
                        .addLimit(
                            Bandwidth.builder()
                                .capacity(properties.actionsPerMinute())
                                .refillGreedy(properties.actionsPerMinute(), Duration.ofMinutes(1))
                                .build())
                        .build());
  }

  public void check(UUID user) {
    var probe = buckets.get(user).tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      throw new DailyRateLimitException(
          Math.max(1, (probe.getNanosToWaitForRefill() + 999999999L) / 1000000000L));
    }
  }
}
