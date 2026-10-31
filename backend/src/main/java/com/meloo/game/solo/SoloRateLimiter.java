package com.meloo.game.solo;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Rate limiter for solo game API endpoints per player.
 *
 * <p>Modeled after AudioRateLimiter and GuestRateLimiter. Separates two rate limit categories:
 *
 * <ul>
 *   <li>Create match: default 10 requests / minute
 *   <li>Other in-match actions: default 60 requests / minute
 * </ul>
 *
 * Automatically evicts inactive player buckets via Caffeine cache to prevent memory leaks.
 */
@Component
public class SoloRateLimiter {

  private static final Duration INACTIVE_BUCKET_TTL = Duration.ofMinutes(10);
  private static final long MAX_CACHED_BUCKETS = 10_000;

  private final SoloProperties.RateLimitProperties properties;
  private final Clock clock;
  private final TimeMeter timeMeter;
  private final LoadingCache<UUID, Bucket> createMatchBuckets;
  private final LoadingCache<UUID, Bucket> actionBuckets;

  @Autowired
  public SoloRateLimiter(SoloProperties properties, ObjectProvider<Clock> clockProvider) {
    this(
        Objects.requireNonNull(properties, "properties must not be null").rateLimit(),
        Objects.requireNonNull(clockProvider, "clockProvider must not be null")
            .getIfAvailable(Clock::systemUTC));
  }

  public SoloRateLimiter(SoloProperties.RateLimitProperties properties, Clock clock) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.timeMeter =
        new TimeMeter() {
          @Override
          public long currentTimeNanos() {
            return clockNanos(SoloRateLimiter.this.clock);
          }

          @Override
          public boolean isWallClockBased() {
            return true;
          }
        };

    this.createMatchBuckets =
        Caffeine.newBuilder()
            .expireAfterAccess(INACTIVE_BUCKET_TTL)
            .maximumSize(MAX_CACHED_BUCKETS)
            .ticker(() -> clockNanos(this.clock))
            .build(this::buildCreateMatchBucket);

    this.actionBuckets =
        Caffeine.newBuilder()
            .expireAfterAccess(INACTIVE_BUCKET_TTL)
            .maximumSize(MAX_CACHED_BUCKETS)
            .ticker(() -> clockNanos(this.clock))
            .build(this::buildActionBucket);
  }

  /**
   * Checks the match creation rate limit for the given player.
   *
   * @throws SoloRateLimitException if the player exceeds match creation quota
   */
  public void checkCreateMatch(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId must not be null");
    consumeOrThrow(createMatchBuckets.get(playerId));
  }

  /**
   * Checks the action rate limit for all other solo game operations.
   *
   * @throws SoloRateLimitException if the player exceeds action quota
   */
  public void checkAction(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId must not be null");
    consumeOrThrow(actionBuckets.get(playerId));
  }

  private void consumeOrThrow(Bucket bucket) {
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      long nanosToWait = probe.getNanosToWaitForRefill();
      long retryAfterSec = Math.max(1, (nanosToWait + 999_999_999L) / 1_000_000_000L);
      throw new SoloRateLimitException(retryAfterSec);
    }
  }

  private Bucket buildCreateMatchBucket(UUID playerId) {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(properties.createMatchCapacity())
            .refillGreedy(
                properties.createMatchRefillTokens(), properties.createMatchRefillDuration())
            .build();
    return Bucket.builder().withCustomTimePrecision(timeMeter).addLimit(limit).build();
  }

  private Bucket buildActionBucket(UUID playerId) {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(properties.actionCapacity())
            .refillGreedy(properties.actionRefillTokens(), properties.actionRefillDuration())
            .build();
    return Bucket.builder().withCustomTimePrecision(timeMeter).addLimit(limit).build();
  }

  private static long clockNanos(Clock clock) {
    Instant now = clock.instant();
    return now.getEpochSecond() * 1_000_000_000L + now.getNano();
  }

  void reset() {
    createMatchBuckets.invalidateAll();
    actionBuckets.invalidateAll();
  }

  void cleanUp() {
    createMatchBuckets.cleanUp();
    actionBuckets.cleanUp();
  }

  long getCreateMatchBucketCount() {
    return createMatchBuckets.estimatedSize();
  }

  long getActionBucketCount() {
    return actionBuckets.estimatedSize();
  }
}
