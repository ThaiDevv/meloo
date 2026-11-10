package com.meloo.daily.service;

import com.meloo.audio.api.AudioService;
import com.meloo.daily.persistence.AttemptStatus;
import com.meloo.daily.persistence.DailyAttempt;
import com.meloo.generated.model.DailyGuessResponseDto;
import com.meloo.generated.model.DailyHistoryResponseDto;
import com.meloo.generated.model.DailyLeaderboardPageDto;
import com.meloo.generated.model.DailyResultDto;
import com.meloo.generated.model.DailyRoundStateDto;
import com.meloo.generated.model.DailyTodayResponseDto;
import com.meloo.shared.api.ApiException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

/**
 * Network audio preparation happens between transactions, including after an application restart.
 */
@Service
public class DailyService {
  private final DailyOperations operations;
  private final DailyMapper mapper;
  private final DailyLeaderboard leaderboard;
  private final AudioService audio;
  private final ThreadPoolTaskScheduler scheduler;
  private final DailyProperties properties;

  public DailyService(
      DailyOperations operations,
      DailyMapper mapper,
      DailyLeaderboard leaderboard,
      AudioService audio,
      @Qualifier("dailyExpiryScheduler") ThreadPoolTaskScheduler scheduler,
      DailyProperties properties) {
    this.operations = operations;
    this.mapper = mapper;
    this.leaderboard = leaderboard;
    this.audio = audio;
    this.scheduler = scheduler;
    this.properties = properties;
  }

  public DailyTodayResponseDto today(UUID user) {
    operations
        .current(user)
        .filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS)
        .ifPresent(this::prepare);
    return operations.state(user);
  }

  public DailyRoundStateDto start(UUID user, boolean guest) {
    for (int retry = 0; retry < 2; retry++) {
      var plan = operations.planStart(user, guest);
      var proposed = plan.attempt();
      try {
        prepare(proposed);
        if (plan.existing()) {
          return operations.round(user, proposed.getId());
        }
        var accepted = operations.acceptStart(plan);
        if (accepted.isPresent()) {
          var attempt = accepted.get();
          if (!attempt.getId().equals(proposed.getId())) {
            audio.releaseRoundAudio(proposed.getId());
          }
          return operations.round(user, attempt.getId());
        }
      } catch (RuntimeException exception) {
        if (!plan.existing()) {
          audio.releaseRoundAudio(proposed.getId());
        }
        throw exception;
      }
      audio.releaseRoundAudio(proposed.getId());
    }
    throw new ApiException(
        HttpStatus.NOT_FOUND, "DAILY_CHALLENGE_NOT_AVAILABLE", "Daily challenge is unavailable");
  }

  public DailyRoundStateDto listen(UUID user, @Nullable Integer expectedLevel) {
    prepare(operations.active(user));
    return operations.listen(user, expectedLevel);
  }

  public DailyGuessResponseDto guess(UUID user, UUID trackId) {
    prepare(operations.active(user));
    return operations.guess(user, trackId);
  }

  public DailyResultDto skip(UUID user) {
    return operations.skip(user);
  }

  public DailyResultDto result(UUID user, @Nullable LocalDate date) {
    return operations.result(user, date);
  }

  public DailyHistoryResponseDto history(
      UUID user, @Nullable LocalDate from, @Nullable LocalDate to) {
    return operations.history(user, from, to);
  }

  public DailyLeaderboardPageDto leaderboard(
      UUID user, boolean guest, @Nullable LocalDate date, int page, int size) {
    if (page < 0 || size < 1 || size > 50) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid pagination");
    }
    expireDueAttempts();
    return leaderboard.page(user, guest, date == null ? operations.today() : date, page, size);
  }

  private void prepare(DailyAttempt a) {
    if (!audio.isRoundPlayable(a.getId())) {
      var track = mapper.track(a);
      if (!audio
          .prepareRoundAudio(a.getId(), track.id(), track.provider(), track.providerTrackId())
          .playable()) {
        throw new ApiException(
            HttpStatus.NOT_FOUND, "DAILY_CHALLENGE_NOT_AVAILABLE", "Daily audio is unavailable");
      }
    }
  }

  /** Own timer avoids enabling unrelated modules' scheduled jobs. */
  @EventListener(ApplicationReadyEvent.class)
  public void scheduleExpiry() {
    Duration delay = Duration.ofMillis(properties.expiryScanMs());
    scheduler.scheduleWithFixedDelay(
        this::expireDueAttempts, scheduler.getClock().instant().plus(delay), delay);
  }

  public void expireDueAttempts() {
    for (UUID id : operations.expiredIds()) {
      operations.expire(id);
    }
  }
}
