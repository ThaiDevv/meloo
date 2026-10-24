package com.meloo.identity.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.meloo.identity.config.IdentityProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class GuestRateLimiter {

  private final int limitPerHour;
  private final LoadingCache<String, Bucket> buckets;

  public GuestRateLimiter(IdentityProperties properties) {
    this.limitPerHour = properties.rateLimit().guestCreatePerHour();
    this.buckets =
        Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(50_000)
            .build(key -> createNewBucket());
  }

  private Bucket createNewBucket() {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(limitPerHour)
            .refillIntervally(limitPerHour, Duration.ofHours(1))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  public RateLimitResult tryAcquire(String ip) {
    Bucket bucket = buckets.get(ip);
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      return RateLimitResult.allowed(probe.getRemainingTokens());
    } else {
      long waitForRefillNanos = probe.getNanosToWaitForRefill();
      long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(waitForRefillNanos));
      return RateLimitResult.rejected(retryAfterSeconds);
    }
  }

  public record RateLimitResult(boolean allowed, long remainingTokens, long retryAfterSeconds) {

    public static RateLimitResult allowed(long remainingTokens) {
      return new RateLimitResult(true, remainingTokens, 0);
    }

    public static RateLimitResult rejected(long retryAfterSeconds) {
      return new RateLimitResult(false, 0, retryAfterSeconds);
    }
  }
}
