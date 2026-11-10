package com.meloo.daily.service;

import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.daily.api.DailyAdminApi;
import com.meloo.daily.events.DailyAttemptFinished;
import com.meloo.daily.persistence.AttemptStatus;
import com.meloo.daily.persistence.ChallengeSource;
import com.meloo.daily.persistence.DailyAttempt;
import com.meloo.daily.persistence.DailyAttemptRepository;
import com.meloo.daily.persistence.DailyChallenge;
import com.meloo.daily.persistence.DailyChallengeRepository;
import com.meloo.daily.persistence.DailyLocks;
import com.meloo.game.domain.ComboTracker;
import com.meloo.game.domain.Round;
import com.meloo.game.domain.RoundOutcome;
import com.meloo.game.domain.TargetTrack;
import com.meloo.generated.model.DailyAttemptStatusDto;
import com.meloo.generated.model.DailyGuessResponseDto;
import com.meloo.generated.model.DailyHistoryItemDto;
import com.meloo.generated.model.DailyHistoryResponseDto;
import com.meloo.generated.model.DailyResultDto;
import com.meloo.generated.model.DailyRoundStateDto;
import com.meloo.generated.model.DailyTodayResponseDto;
import com.meloo.identity.api.AccountLockQuery;
import com.meloo.identity.events.GuestMerged;
import com.meloo.shared.api.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All mutations and completion events share a database transaction and a per-player lock. */
@Service
@Transactional(noRollbackFor = ApiException.class)
public class DailyOperations implements DailyAdminApi {
  private final DailyAttemptRepository attempts;
  private final DailyChallengeRepository challenges;
  private final DailyLocks locks;
  private final CatalogService catalog;
  private final AccountLockQuery accountLocks;
  private final DailyMapper mapper;
  private final DailyLeaderboard leaderboard;
  private final DailyProperties properties;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public DailyOperations(
      DailyAttemptRepository attempts,
      DailyChallengeRepository challenges,
      DailyLocks locks,
      CatalogService catalog,
      AccountLockQuery accountLocks,
      DailyMapper mapper,
      DailyLeaderboard leaderboard,
      DailyProperties properties,
      ApplicationEventPublisher events,
      Clock clock) {
    this.attempts = attempts;
    this.challenges = challenges;
    this.locks = locks;
    this.catalog = catalog;
    this.accountLocks = accountLocks;
    this.mapper = mapper;
    this.leaderboard = leaderboard;
    this.properties = properties;
    this.events = events;
    this.clock = clock;
  }

  public LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), DailyMapper.VIETNAM);
  }

  private void lockPlayer(UUID user) {
    locks.acquire("player:" + user);
    if (locks.merged(user) || accountLocks.isLocked(user)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Account is unavailable");
    }
  }

  /** A read may finalize an expired attempt, but an error must still commit that completion. */
  public Optional<DailyAttempt> current(UUID user) {
    lockPlayer(user);
    expirePlayer(user);
    var active = attempts.findByUserIdAndStatusOrderByStartedAtAsc(user, AttemptStatus.IN_PROGRESS);
    return active.isEmpty()
        ? attempts.findByUserIdAndChallengeDate(user, today())
        : Optional.of(active.getFirst());
  }

  public record StartPlan(DailyAttempt attempt, boolean existing) {}

  public StartPlan planStart(UUID user, boolean guest) {
    var current = current(user);
    if (current.isPresent()) {
      if (current.get().getStatus() == AttemptStatus.IN_PROGRESS) {
        return new StartPlan(current.get(), true);
      }
      throw conflict("DAILY_ALREADY_PLAYED");
    }
    var challenge = challenge(today());
    TrackInfo track =
        catalog.getTrackById(challenge.getTrackId()).orElseThrow(DailyOperations::unavailable);
    var attempt =
        DailyAttempt.builder()
            .id(UUID.randomUUID())
            .userId(user)
            .guest(guest)
            .challengeDate(challenge.getChallengeDate())
            .status(AttemptStatus.IN_PROGRESS)
            .startedAt(clock.instant())
            .trackSnapshot(mapper.snapshot(track))
            .build();
    return new StartPlan(attempt, false);
  }

  /** Recheck after network preparation; only a playable, issued attempt consumes the day. */
  public Optional<DailyAttempt> acceptStart(StartPlan plan) {
    DailyAttempt draft = plan.attempt();
    var current = current(draft.getUserId());
    if (current.isPresent()) {
      if (current.get().getStatus() == AttemptStatus.IN_PROGRESS) {
        return current;
      }
      throw conflict("DAILY_ALREADY_PLAYED");
    }
    if (!draft.getChallengeDate().equals(today())) {
      return Optional.empty(); // Preparation crossed midnight: resolve the new day's assignment.
    }
    draft.setStartedAt(clock.instant());
    return Optional.of(attempts.saveAndFlush(draft));
  }

  private DailyChallenge challenge(LocalDate date) {
    locks.acquire("date:" + date);
    var existing = challenges.findById(date);
    if (existing.isPresent()) {
      return existing.get();
    }
    var popular = catalog.selectPopularTracks(properties.popularPoolSize());
    if (popular.isEmpty()) {
      throw unavailable();
    }
    var track = popular.get(ThreadLocalRandom.current().nextInt(popular.size()));
    return challenges.saveAndFlush(
        DailyChallenge.builder()
            .challengeDate(date)
            .trackId(track.id())
            .source(ChallengeSource.AUTO)
            .createdAt(clock.instant())
            .build());
  }

  public DailyTodayResponseDto state(UUID user) {
    var current = current(user);
    var date = current.map(DailyAttempt::getChallengeDate).orElseGet(this::today);
    if (current.isEmpty()) {
      challenge(date);
    }
    var history = attempts.findByUserIdOrderByChallengeDateAsc(user);
    var response =
        new DailyTodayResponseDto()
            .challengeDate(date)
            .status(
                current
                    .map(a -> DailyAttemptStatusDto.fromValue(a.getStatus().name()))
                    .orElse(DailyAttemptStatusDto.NOT_STARTED))
            .currentStreak(DailyStreaks.current(history, today()))
            .longestStreak(DailyStreaks.longest(history, LocalDate.MIN, LocalDate.MAX))
            .nextResetAt(DailyMapper.resetAfter(today()))
            .serverTime(clock.instant().atOffset(ZoneOffset.UTC));
    current.ifPresent(
        a -> {
          if (a.getStatus() == AttemptStatus.IN_PROGRESS) {
            response.setRound(mapper.round(a, clock.instant()));
          } else {
            response.setResult(completed(a));
          }
        });
    return response;
  }

  public DailyAttempt active(UUID user) {
    return current(user)
        .filter(a -> a.getStatus() == AttemptStatus.IN_PROGRESS)
        .orElseThrow(() -> conflict("ROUND_NOT_ACTIVE"));
  }

  public DailyRoundStateDto round(UUID user, UUID expectedAttempt) {
    var a = active(user);
    if (!a.getId().equals(expectedAttempt)) {
      throw conflict("ROUND_NOT_ACTIVE");
    }
    return mapper.round(a, clock.instant());
  }

  public DailyRoundStateDto listen(UUID user, @Nullable Integer expectedLevel) {
    var a = active(user);
    if (a.getCurrentLevel() == 5) {
      throw conflict("LISTEN_LEVEL_MAX");
    }
    if (expectedLevel != null && expectedLevel != a.getCurrentLevel() + 1) {
      throw conflict("LISTEN_LEVEL_MISMATCH");
    }
    Round round = engine(a);
    round.listenMore();
    a.setCurrentLevel((short) round.currentLevel());
    return mapper.round(a, clock.instant());
  }

  public DailyGuessResponseDto guess(UUID user, UUID guessedTrack) {
    var a = active(user);
    TrackInfo guessed =
        catalog
            .getTrackById(guessedTrack)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "TRACK_NOT_FOUND", "Track not found"));
    Round round = engine(a);
    var evaluation =
        round.submitGuess(
            guessed.title(), guessed.artistName(), clock.instant(), new ComboTracker(false));
    a.setWrongCount(a.getWrongCount() + (evaluation.correct() ? 0 : 1));
    a.setCurrentLevel((short) round.currentLevel());
    var response =
        new DailyGuessResponseDto()
            .correct(evaluation.correct())
            .rightArtist(evaluation.rightArtist())
            .attemptEnded(evaluation.roundEnded());
    if (evaluation.roundEnded()) {
      finish(a, round.outcome(), clock.instant());
      response.setResult(completed(a));
    } else {
      response.setRound(mapper.round(a, clock.instant()));
    }
    return response;
  }

  public DailyResultDto skip(UUID user) {
    var a = active(user);
    if (a.getCurrentLevel() != 5) {
      throw conflict("SKIP_NOT_ALLOWED");
    }
    Round round = engine(a);
    round.skip(clock.instant(), new ComboTracker(false));
    finish(a, round.outcome(), clock.instant());
    return completed(a);
  }

  public DailyResultDto result(UUID user, @Nullable LocalDate date) {
    current(user);
    var a =
        attempts
            .findByUserIdAndChallengeDate(user, date == null ? today() : date)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Result not found"));
    if (a.getStatus() == AttemptStatus.IN_PROGRESS) {
      throw conflict("ROUND_NOT_FINISHED");
    }
    return completed(a);
  }

  public DailyHistoryResponseDto history(
      UUID user, @Nullable LocalDate from, @Nullable LocalDate to) {
    current(user);
    LocalDate end = to == null ? today() : to;
    LocalDate start = from == null ? end.minusDays(279) : from;
    validateRange(start, end);
    var history = attempts.findByUserIdOrderByChallengeDateAsc(user);
    return new DailyHistoryResponseDto()
        .today(today())
        .items(
            history.stream()
                .filter(a -> a.getStatus() == AttemptStatus.SOLVED)
                .filter(
                    a ->
                        !a.getChallengeDate().isBefore(start) && !a.getChallengeDate().isAfter(end))
                .map(
                    a ->
                        new DailyHistoryItemDto()
                            .date(a.getChallengeDate())
                            .level(DailyMapper.seconds(a.getCorrectLevel())))
                .toList())
        .currentStreak(DailyStreaks.current(history, today()))
        .longestStreak(DailyStreaks.longest(history, start, end));
  }

  private DailyResultDto completed(DailyAttempt a) {
    attempts.flush();
    var history = attempts.findByUserIdOrderByChallengeDateAsc(a.getUserId());
    var result =
        mapper.result(
            a,
            DailyStreaks.current(history, today()),
            DailyStreaks.longest(history, LocalDate.MIN, LocalDate.MAX),
            leaderboard.rank(a.getUserId(), a.isGuest(), a.getChallengeDate()));
    result.setNextResetAt(DailyMapper.resetAfter(today()));
    return result;
  }

  private Round engine(DailyAttempt a) {
    TrackInfo track = mapper.track(a);
    Round round =
        Round.standardRound(
            1, new TargetTrack(track.id(), track.title(), track.artistName()), false);
    round.start(a.getStartedAt());
    for (int i = 1; i < a.getCurrentLevel(); i++) {
      round.listenMore();
    }
    return round;
  }

  private void finish(DailyAttempt a, @Nullable RoundOutcome outcome, Instant finishedAt) {
    if (a.getStatus() != AttemptStatus.IN_PROGRESS) {
      return;
    }
    a.setStatus(outcome == RoundOutcome.CORRECT ? AttemptStatus.SOLVED : AttemptStatus.UNSOLVED);
    a.setOutcome(java.util.Objects.requireNonNull(outcome).name());
    a.setFinishedAt(finishedAt);
    a.setCorrectLevel(outcome == RoundOutcome.CORRECT ? a.getCurrentLevel() : null);
    a.setElapsedMs((int) Math.max(0, Duration.between(a.getStartedAt(), finishedAt).toMillis()));
    events.publishEvent(
        new DailyAttemptFinished(
            a.getId(),
            a.getId(),
            a.getUserId(),
            a.isGuest(),
            a.getChallengeDate(),
            a.getStatus().name(),
            outcome.name(),
            a.getCorrectLevel() == null ? null : a.getCorrectLevel().intValue(),
            DailyMapper.score(a),
            a.getElapsedMs(),
            finishedAt));
  }

  private void expirePlayer(UUID user) {
    for (var a :
        attempts.findByUserIdAndStatusOrderByStartedAtAsc(user, AttemptStatus.IN_PROGRESS)) {
      if (!clock.instant().isBefore(a.getStartedAt().plusSeconds(DailyMapper.ATTEMPT_SECONDS))) {
        Round round = engine(a);
        Instant expiresAt = a.getStartedAt().plusSeconds(DailyMapper.ATTEMPT_SECONDS);
        round.timeout(expiresAt, new ComboTracker(false));
        finish(a, round.outcome(), expiresAt);
      }
    }
  }

  public List<UUID> expiredIds() {
    return attempts.findExpired(clock.instant().minusSeconds(DailyMapper.ATTEMPT_SECONDS));
  }

  public void expire(UUID id) {
    attempts
        .findOwner(id)
        .ifPresent(
            user -> {
              locks.acquire("player:" + user);
              // Lock the actual row too: GuestMerged may have changed its owner before the player
              // lock.
              attempts
                  .findLockedById(id)
                  .ifPresent(
                      a -> {
                        Instant expiresAt =
                            a.getStartedAt().plusSeconds(DailyMapper.ATTEMPT_SECONDS);
                        if (a.getStatus() == AttemptStatus.IN_PROGRESS
                            && !clock.instant().isBefore(expiresAt)) {
                          finish(a, RoundOutcome.TIMEOUT, expiresAt);
                        }
                      });
            });
  }

  public void merge(GuestMerged event) {
    List.of(event.guestId(), event.userId()).stream()
        .sorted()
        .forEach(id -> locks.acquire("player:" + id));
    if (locks.merged(event.guestId())) {
      return;
    }
    var guestAttempts = attempts.findByUserIdOrderByChallengeDateAsc(event.guestId());
    var accountDates =
        attempts.findByUserIdOrderByChallengeDateAsc(event.userId()).stream()
            .map(DailyAttempt::getChallengeDate)
            .collect(java.util.stream.Collectors.toSet());
    for (var a : guestAttempts) {
      if (accountDates.contains(a.getChallengeDate())) {
        attempts.delete(a);
      } else {
        a.setUserId(event.userId());
        a.setGuest(false);
      }
    }
    attempts.flush();
    locks.markMerged(event.guestId(), event.userId());
    expirePlayer(event.userId());
  }

  @Override
  @Transactional(readOnly = true)
  public List<ScheduleItem> listSchedule(LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid schedule range");
    }
    return challenges.findByChallengeDateBetweenOrderByChallengeDateAsc(from, to).stream()
        .map(DailyOperations::scheduleItem)
        .toList();
  }

  @Override
  public ScheduleChange schedule(LocalDate date, UUID trackId, UUID adminId) {
    locks.acquire("date:" + date);
    if (!date.isAfter(today())) {
      throw conflict("DAILY_CHALLENGE_LOCKED");
    }
    catalog
        .getTrackById(trackId)
        .orElseThrow(
            () -> new ApiException(HttpStatus.NOT_FOUND, "TRACK_NOT_FOUND", "Track not found"));
    var prior = challenges.findById(date).map(DailyOperations::scheduleItem).orElse(null);
    var challenge =
        DailyChallenge.builder()
            .challengeDate(date)
            .trackId(trackId)
            .source(ChallengeSource.SCHEDULED)
            .setBy(adminId)
            .createdAt(prior == null ? clock.instant() : prior.createdAt())
            .build();
    return new ScheduleChange(prior, scheduleItem(challenges.saveAndFlush(challenge)));
  }

  private static ScheduleItem scheduleItem(DailyChallenge challenge) {
    return new ScheduleItem(
        challenge.getChallengeDate(),
        challenge.getTrackId(),
        challenge.getSource().name(),
        challenge.getSetBy(),
        challenge.getCreatedAt());
  }

  static void validateRange(LocalDate from, LocalDate to) {
    if (from == null
        || to == null
        || from.isAfter(to)
        || ChronoUnit.DAYS.between(from, to) >= 366) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid date range (maximum 366 days)");
    }
  }

  private static ApiException conflict(String code) {
    return new ApiException(HttpStatus.CONFLICT, code, "Daily action is unavailable");
  }

  private static ApiException unavailable() {
    return new ApiException(
        HttpStatus.NOT_FOUND, "DAILY_CHALLENGE_NOT_AVAILABLE", "Daily challenge is unavailable");
  }
}
