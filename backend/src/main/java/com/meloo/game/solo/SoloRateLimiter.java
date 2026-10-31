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
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Giới hạn tần suất gọi API chơi đơn theo người chơi.
 *
 * <p>Theo mẫu AudioRateLimiter và GuestRateLimiter. Tách biệt 2 loại giới hạn:
 *
 * <ul>
 *   <li>Tạo trận: mặc định 10 lượt / phút
 *   <li>Các thao tác khác trong trận: mặc định 60 lượt / phút
 * </ul>
 *
 * Tự động dọn dẹp các bucket không hoạt động thông qua Caffeine cache để chống rò rỉ bộ nhớ.
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
   * Kiểm tra giới hạn tạo trận mới cho người chơi.
   *
   * @throws SoloRateLimitException nếu vượt quá giới hạn tạo trận (kèm retryAfter)
   */
  public void checkCreateMatch(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId must not be null");
    consumeOrThrow(createMatchBuckets.get(playerId));
  }

  /**
   * Kiểm tra giới hạn cho các thao tác chơi đơn khác (đoán, nghe thêm, bỏ qua, lấy kết quả,...).
   *
   * @throws SoloRateLimitException nếu vượt quá giới hạn thao tác (kèm retryAfter)
   */
  public void checkAction(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId must not be null");
    consumeOrThrow(actionBuckets.get(playerId));
  }

  private void consumeOrThrow(Bucket bucket) {
    if (bucket == null) {
      return;
    }
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      long retryAfterSec =
          Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
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

  /** Xóa toàn bộ bucket được lưu tạm (dùng cho test). */
  public void reset() {
    createMatchBuckets.invalidateAll();
    actionBuckets.invalidateAll();
  }

  /** Kích hoạt dọn dẹp các mục hết hạn theo thời gian. */
  public void cleanUp() {
    createMatchBuckets.cleanUp();
    actionBuckets.cleanUp();
  }

  /** Số lượng bucket tạo trận đang nằm trong cache. */
  public long getCreateMatchBucketCount() {
    return createMatchBuckets.estimatedSize();
  }

  /** Số lượng bucket thao tác đang nằm trong cache. */
  public long getActionBucketCount() {
    return actionBuckets.estimatedSize();
  }
}
