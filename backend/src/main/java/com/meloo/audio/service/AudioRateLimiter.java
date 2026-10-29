package com.meloo.audio.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meloo.audio.config.AudioProperties;
import com.meloo.audio.domain.AudioRateLimitException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AudioRateLimiter {

  private final AudioProperties.RateLimitProperties properties;
  private final Cache<String, Bucket> tokenBuckets;
  private final Cache<UUID, Bucket> playerBuckets;

  public AudioRateLimiter(AudioProperties.RateLimitProperties properties) {
    this.properties = properties;
    this.tokenBuckets =
        Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(3)).maximumSize(20_000).build();
    this.playerBuckets =
        Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(5)).maximumSize(10_000).build();
  }

  public void checkRateLimit(String token, UUID playerId) {
    Bucket tokenBucket = tokenBuckets.get(token, k -> buildTokenBucket());
    if (tokenBucket != null) {
      ConsumptionProbe probe = tokenBucket.tryConsumeAndReturnRemaining(1);
      if (!probe.isConsumed()) {
        long retryAfterSec =
            Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        throw new AudioRateLimitException(retryAfterSec);
      }
    }

    Bucket playerBucket = playerBuckets.get(playerId, k -> buildPlayerBucket());
    if (playerBucket != null) {
      ConsumptionProbe probe = playerBucket.tryConsumeAndReturnRemaining(1);
      if (!probe.isConsumed()) {
        long retryAfterSec =
            Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        throw new AudioRateLimitException(retryAfterSec);
      }
    }
  }

  private Bucket buildTokenBucket() {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(properties.tokenCapacity())
            .refillGreedy(properties.tokenRefillTokens(), properties.tokenRefillDuration())
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  private Bucket buildPlayerBucket() {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(properties.playerCapacity())
            .refillGreedy(properties.playerRefillTokens(), properties.playerRefillDuration())
            .build();
    return Bucket.builder().addLimit(limit).build();
  }
}
