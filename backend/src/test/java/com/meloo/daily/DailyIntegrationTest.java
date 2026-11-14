package com.meloo.daily;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meloo.IntegrationTest;
import com.meloo.audio.api.AudioPreparationResult;
import com.meloo.audio.api.AudioService;
import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.daily.api.DailyAdminApi;
import com.meloo.daily.events.DailyAttemptFinished;
import com.meloo.daily.persistence.AttemptStatus;
import com.meloo.daily.persistence.DailyAttempt;
import com.meloo.daily.persistence.DailyAttemptRepository;
import com.meloo.daily.service.DailyMapper;
import com.meloo.daily.service.DailyOperations;
import com.meloo.daily.service.DailyService;
import com.meloo.generated.model.AudioClipDto;
import com.meloo.generated.model.DailyLevelStatusDto;
import com.meloo.identity.events.GuestMerged;
import com.meloo.shared.api.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Import(DailyIntegrationTest.Config.class)
class DailyIntegrationTest extends IntegrationTest {
  static final Instant NOON = Instant.parse("2026-11-10T05:00:00Z");
  static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
  static final TrackInfo TARGET = track("Nơi Này Có Anh", "Sơn Tùng M-TP");
  static final TrackInfo COVER = track("Noi Nay Co Anh (Acoustic)", "Cover Artist");
  static final TrackInfo WRONG = track("Lạc Trôi", "Sơn Tùng M-TP");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add(
        "MELOO_JWT_SECRET", () -> "test-meloo-jwt-secret-key-with-minimum-32-bytes-length-xyz");
    registry.add("meloo.audio.provider.type", () -> "fake");
    registry.add(
        "meloo.audio.hmac-secret", () -> "test-hmac-secret-key-with-minimum-32-bytes-length-xyz");
    registry.add("meloo.daily.expiry-scan-ms", () -> "3600000");
  }

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    TestClock testClock() {
      return new TestClock();
    }

    @Bean
    CapturedEvents capturedEvents() {
      return new CapturedEvents();
    }
  }

  static class TestClock extends Clock {
    volatile Instant now = NOON;

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }

    void advance(long seconds) {
      now = now.plusSeconds(seconds);
    }
  }

  static class CapturedEvents {
    final List<DailyAttemptFinished> finished = new CopyOnWriteArrayList<>();
    final AtomicBoolean fail = new AtomicBoolean();

    @EventListener
    void onFinished(DailyAttemptFinished event) {
      if (fail.get()) {
        throw new IllegalStateException("Simulated event publication failure");
      }
      finished.add(event);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired DailyService daily;
  @Autowired DailyOperations operations;
  @Autowired DailyAdminApi admin;
  @Autowired DailyAttemptRepository attempts;
  @Autowired DailyMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired TestClock clock;
  @Autowired CapturedEvents events;
  @Autowired ApplicationEventPublisher publisher;
  @MockitoBean CatalogService catalog;
  @MockitoBean AudioService audio;
  final Set<UUID> prepared = ConcurrentHashMap.newKeySet();
  final Map<UUID, TrackInfo> tracks = new ConcurrentHashMap<>();

  @BeforeEach
  void setup() {
    jdbc.execute("truncate daily_guest_merge, daily_attempt, daily_challenge");
    clock.now = NOON;
    events.finished.clear();
    events.fail.set(false);
    prepared.clear();
    tracks.clear();
    for (var track : List.of(TARGET, COVER, WRONG)) {
      tracks.put(track.id(), track);
    }
    when(catalog.getTrackById(any()))
        .thenAnswer(i -> Optional.ofNullable(tracks.get(i.getArgument(0))));
    when(catalog.selectPopularTracks(anyInt())).thenReturn(List.of(TARGET));
    when(audio.isRoundPlayable(any())).thenAnswer(i -> prepared.contains(i.<UUID>getArgument(0)));
    when(audio.prepareRoundAudio(any(), any(), any(), any()))
        .thenAnswer(
            i -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              prepared.add(i.getArgument(0));
              return AudioPreparationResult.success();
            });
    when(audio.createAudioClip(any(), any(), anyInt()))
        .thenReturn(
            new AudioClipDto()
                .url("/api/v1/audio/previews/opaque-token")
                .expiresAt(NOON.plusSeconds(120).atOffset(ZoneOffset.UTC)));
    doAnswer(i -> prepared.remove(i.<UUID>getArgument(0))).when(audio).releaseRoundAudio(any());
  }

  @Test
  void guestLifecycleDoesNotLeakAnswerAndUsesNormalizedEngineAndFiveShareSlots() throws Exception {
    UUID user = user(true);
    var fresh =
        body(
            mvc.perform(get("/api/v1/daily/today").with(auth(user, true)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(fresh.path("status").asText()).isEqualTo("NOT_STARTED");
    var start =
        mvc.perform(post("/api/v1/daily/today/start").with(auth(user, true)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertNoAnswer(start);
    assertThat(body(start).path("level").asInt()).isEqualTo(1);
    mvc.perform(get("/api/v1/daily/result").with(auth(user, true)))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/v1/daily/today/skip").with(auth(user, true)))
        .andExpect(status().isConflict());
    clock.advance(7);
    String wrong = guessHttp(user, true, WRONG.id());
    assertNoAnswer(wrong);
    assertThat(body(wrong).path("rightArtist").asBoolean()).isTrue();
    assertThat(body(wrong).path("round").path("level").asInt()).isEqualTo(2);
    var result = body(guessHttp(user, true, COVER.id())).path("result");
    assertThat(result.path("status").asText()).isEqualTo("SOLVED");
    assertThat(result.path("score").asInt()).isEqualTo(800);
    assertThat(result.path("elapsedMs").asInt()).isEqualTo(7000);
    assertThat(result.path("wrongCount").asInt()).isEqualTo(1);
    assertThat(result.path("levels").toString())
        .isEqualTo("[\"WRONG\",\"CORRECT\",\"UNOPENED\",\"UNOPENED\",\"UNOPENED\"]");
    assertThat(result.path("answer").path("title").asText()).isEqualTo(TARGET.title());
    assertThat(result.hasNonNull("rank")).isFalse();
    mvc.perform(post("/api/v1/daily/today/start").with(auth(user, true)))
        .andExpect(status().isConflict());
    assertThat(daily.leaderboard(user, true, null, 0, 10).getItems()).isEmpty();
    assertThat(events.finished).hasSize(1);
    assertThat(events.finished.getFirst().isGuest()).isTrue();
    assertThat(events.finished.getFirst().eventId())
        .isEqualTo(events.finished.getFirst().attemptId());
    assertThat(prepared).isEmpty();
  }

  @Test
  void openingLevelsGuardsDoubleClicksAndSkippingRevealsOnlyAfterLevelFive() {
    UUID user = user(false);
    daily.start(user, false);
    assertCode(() -> daily.listen(user, 3), "LISTEN_LEVEL_MISMATCH");
    for (int level = 2; level <= 5; level++) {
      assertThat(daily.listen(user, level).getLevel()).isEqualTo(level);
    }
    assertCode(() -> daily.listen(user, 5), "LISTEN_LEVEL_MAX");
    var result = daily.skip(user);
    assertThat(result.getOutcome().getValue()).isEqualTo("GAVE_UP");
    assertThat(result.getLevels()).containsOnly(DailyLevelStatusDto.WRONG).hasSize(5);
    assertThat(result.getWrongCount()).isZero();
    assertThat(result.getScore().getValue()).isZero();
    assertThat(events.finished).hasSize(1);
  }

  @Test
  void fiveWrongGuessesEndAttemptWithoutComboAndMissingTrackDoesNotConsumeLevel() {
    UUID user = user(false);
    daily.start(user, false);
    assertCode(() -> daily.guess(user, UUID.randomUUID()), "TRACK_NOT_FOUND");
    for (int guess = 1; guess <= 5; guess++) {
      var response = daily.guess(user, WRONG.id());
      assertThat(response.getAttemptEnded()).isEqualTo(guess == 5);
      if (guess == 5) {
        assertThat(response.getResult().getOutcome().getValue()).isEqualTo("WRONG_OUT");
        assertThat(response.getResult().getWrongCount()).isEqualTo(5);
      }
    }
  }

  @Test
  void issuedDateAndTrackSurviveMidnightRestartAndSnapshotMetadataChanges() {
    clock.now = Instant.parse("2026-11-10T16:59:00Z"); // 23:59 Vietnam.
    UUID user = user(false);
    daily.start(user, false);
    daily.listen(user, 2);
    prepared.clear(); // Simulate loss of in-memory audio bindings on restart.
    tracks.put(TARGET.id(), trackWithId(TARGET.id(), "Changed title", "Changed artist"));
    clock.advance(120);
    assertThat(daily.start(user, false).getChallengeDate()).isEqualTo(TODAY);
    assertThat(daily.today(user).getRound().getLevel()).isEqualTo(2);
    var result = daily.guess(user, COVER.id()).getResult();
    assertThat(result.getChallengeDate()).isEqualTo(TODAY);
    assertThat(result.getAnswer().getTitle()).isEqualTo(TARGET.title());
    assertThat(result.getElapsedMs()).isEqualTo(120000);
    assertThat(result.getNextResetAt().toInstant())
        .isEqualTo(Instant.parse("2026-11-11T17:00:00Z"));
    assertThat(daily.history(user, null, null).getItems().getFirst().getDate()).isEqualTo(TODAY);
    assertThat(daily.start(user, false).getChallengeDate()).isEqualTo(TODAY.plusDays(1));
  }

  @Test
  void timeoutAtExactCeilingCommitsDespite409AndPublishesOnlyOnce() {
    UUID user = user(false);
    daily.start(user, false);
    clock.advance(300);
    assertCode(() -> daily.guess(user, TARGET.id()), "ROUND_NOT_ACTIVE");
    var result = daily.result(user, null);
    assertThat(result.getOutcome().getValue()).isEqualTo("TIMEOUT");
    assertThat(result.getElapsedMs()).isEqualTo(300000);
    assertThat(result.getLevels())
        .containsExactly(
            DailyLevelStatusDto.WRONG,
            DailyLevelStatusDto.UNOPENED,
            DailyLevelStatusDto.UNOPENED,
            DailyLevelStatusDto.UNOPENED,
            DailyLevelStatusDto.UNOPENED);
    daily.expireDueAttempts();
    assertThat(events.finished).hasSize(1);
  }

  @Test
  void expiredYesterdayAttemptFinalizesOnIssuedDateBeforeStartingToday() {
    clock.now = Instant.parse("2026-11-10T16:59:00Z");
    UUID user = user(false);
    daily.start(user, false);
    clock.advance(301);
    assertThat(daily.start(user, false).getChallengeDate()).isEqualTo(TODAY.plusDays(1));
    assertThat(daily.result(user, TODAY).getOutcome().getValue()).isEqualTo("TIMEOUT");
    assertThat(events.finished.getFirst().challengeDate()).isEqualTo(TODAY);
  }

  @Test
  void schedulerFinishesAbandonedAttemptsAndRollsBackWithEventFailure() {
    UUID user = user(false);
    daily.start(user, false);
    events.fail.set(true);
    clock.advance(301);
    assertThatThrownBy(daily::expireDueAttempts).isInstanceOf(IllegalStateException.class);
    assertThat(attempts.findByUserIdAndChallengeDate(user, TODAY).orElseThrow().getStatus())
        .isEqualTo(AttemptStatus.IN_PROGRESS);
    events.fail.set(false);
    daily.expireDueAttempts();
    daily.expireDueAttempts();
    assertThat(events.finished).hasSize(1);
    assertThat(daily.result(user, null).getStatus().getValue()).isEqualTo("UNSOLVED");
  }

  @Test
  void failedInitialAudioDoesNotConsumeTheDailyAttempt() {
    UUID user = user(false);
    doReturn(AudioPreparationResult.failure("unavailable"))
        .when(audio)
        .prepareRoundAudio(any(), any(), any(), any());
    assertCode(() -> daily.start(user, false), "DAILY_CHALLENGE_NOT_AVAILABLE");
    assertThat(attempts.findByUserIdOrderByChallengeDateAsc(user)).isEmpty();
    clock.advance(301);
    doReturn(AudioPreparationResult.success())
        .when(audio)
        .prepareRoundAudio(any(), any(), any(), any());
    assertThat(daily.start(user, false).getLevel()).isEqualTo(1);
    assertThat(attempts.findByUserIdOrderByChallengeDateAsc(user)).hasSize(1);
    assertThat(attempts.findByUserIdAndChallengeDate(user, TODAY).orElseThrow().getStartedAt())
        .isEqualTo(clock.instant());
  }

  @Test
  void initialAudioCrossingMidnightUsesNewDaysScheduledTrackWithoutChargingYesterday() {
    UUID user = user(false);
    admin.schedule(TODAY.plusDays(1), WRONG.id(), user);
    clock.now = Instant.parse("2026-11-10T16:59:59Z");
    List<UUID> preparedTracks = new ArrayList<>();
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              preparedTracks.add(invocation.getArgument(1));
              prepared.add(invocation.getArgument(0));
              clock.now = Instant.parse("2026-11-10T17:00:01Z");
              return AudioPreparationResult.success();
            })
        .when(audio)
        .prepareRoundAudio(any(), any(), any(), any());
    var state = daily.start(user, false);
    assertThat(state.getChallengeDate()).isEqualTo(TODAY.plusDays(1));
    assertThat(state.getStartedAt().toInstant()).isEqualTo(clock.instant());
    assertThat(preparedTracks).containsExactly(TARGET.id(), WRONG.id());
    assertThat(prepared).hasSize(1);
    assertThat(attempts.findByUserIdAndChallengeDate(user, TODAY)).isEmpty();
    assertThat(daily.guess(user, WRONG.id()).getCorrect()).isTrue();
  }

  @Test
  void adminScheduleOverridesAutoAndPastOrCurrentAssignmentsAreLocked() {
    UUID adminId = user(false);
    var future = TODAY.plusDays(1);
    var first = admin.schedule(future, WRONG.id(), adminId);
    assertThat(first.before()).isNull();
    assertThat(first.after().source()).isEqualTo("SCHEDULED");
    var changed = admin.schedule(future, COVER.id(), adminId);
    assertThat(changed.before().trackId()).isEqualTo(WRONG.id());
    assertThat(admin.listSchedule(TODAY, future)).hasSize(1);
    assertCode(() -> admin.schedule(TODAY, TARGET.id(), adminId), "DAILY_CHALLENGE_LOCKED");
    assertCode(
        () -> admin.schedule(TODAY.minusDays(1), TARGET.id(), adminId), "DAILY_CHALLENGE_LOCKED");
    assertCode(() -> admin.schedule(future, UUID.randomUUID(), adminId), "TRACK_NOT_FOUND");
    clock.advance(86400);
    daily.start(adminId, false);
    assertThat(daily.guess(adminId, COVER.id()).getCorrect()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select track_id from daily_challenge where challenge_date = ?",
                UUID.class,
                future))
        .isEqualTo(COVER.id());
  }

  @Test
  void noAvailablePopularTrackReturnsContract404AndDoesNotCreateAttempt() {
    when(catalog.selectPopularTracks(anyInt())).thenReturn(List.of());
    UUID user = user(false);
    assertCode(() -> daily.start(user, false), "DAILY_CHALLENGE_NOT_AVAILABLE");
    assertThat(attempts.findByUserIdOrderByChallengeDateAsc(user)).isEmpty();
  }

  @Test
  void historyIsSparsePrivateAndLongestIsClippedButCurrentIsGlobal() throws Exception {
    UUID user = user(true);
    UUID stranger = user(false);
    assertThat(daily.history(user, null, null).getItems()).isEmpty();
    assertThat(daily.history(user, null, null).getCurrentStreak()).isZero();
    for (int i = -4; i < 0; i++) {
      saved(user, true, TODAY.plusDays(i), AttemptStatus.SOLVED, 1, 1000);
    }
    saved(stranger, false, TODAY, AttemptStatus.SOLVED, 1, 1000);
    var history = daily.history(user, TODAY.minusDays(2), TODAY);
    assertThat(history.getCurrentStreak()).isEqualTo(4);
    assertThat(history.getLongestStreak()).isEqualTo(2);
    assertThat(history.getItems()).extracting(i -> i.getLevel().getValue()).containsExactly(2, 2);
    assertThat(history.getItems())
        .extracting(i -> i.getDate())
        .containsExactly(TODAY.minusDays(2), TODAY.minusDays(1));
    var response =
        mvc.perform(get("/api/v1/daily/history").with(auth(user, true)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertNoAnswer(response);
    saved(user, true, TODAY, AttemptStatus.UNSOLVED, null, 1000);
    assertThat(daily.history(user, null, null).getCurrentStreak()).isZero();
    assertThat(daily.history(user, null, null).getItems()).hasSize(4);
  }

  @Test
  void historyMissingDayAndUnsolvedDayBreakStreakWhileUnfinishedTodayKeepsYesterday() {
    UUID user = user(false);
    saved(user, false, TODAY.minusDays(5), AttemptStatus.SOLVED, 3, 1000);
    saved(user, false, TODAY.minusDays(4), AttemptStatus.UNSOLVED, null, 1000);
    saved(user, false, TODAY.minusDays(3), AttemptStatus.SOLVED, 4, 1000);
    saved(user, false, TODAY.minusDays(1), AttemptStatus.SOLVED, 5, 1000);
    daily.start(user, false);
    assertThat(daily.history(user, null, null).getCurrentStreak()).isEqualTo(1);
    assertThat(daily.history(user, null, null).getLongestStreak()).isEqualTo(1);
    assertThat(daily.history(user, null, null).getItems())
        .extracting(i -> i.getLevel().getValue())
        .containsExactly(10, 20, 30);
    clock.advance(301);
    assertThat(daily.history(user, null, null).getCurrentStreak()).isZero();
    clock.advance(86400);
    assertThat(daily.history(user, null, null).getCurrentStreak()).isZero();
  }

  @Test
  void historyValidates366DayBoundaryDatesAndAuthentication() throws Exception {
    UUID user = user(false);
    mvc.perform(get("/api/v1/daily/history")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/daily/history?from=bad").with(auth(user, false)))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/daily/history?from=2025-11-10&to=2026-11-10").with(auth(user, false)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/daily/history?from=2025-11-09&to=2026-11-10").with(auth(user, false)))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/daily/history?from=2026-11-11&to=2026-11-10").with(auth(user, false)))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/leaderboards/daily?page=-1").with(auth(user, false)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/daily/today/guesses")
                .with(auth(user, false))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void allDailyRoutesCompleteAnHttpJourneyAndReturnRankAndHistory() throws Exception {
    UUID user = user(false);
    mvc.perform(post("/api/v1/daily/today/start").with(auth(user, false)))
        .andExpect(status().isOk());
    String opened =
        mvc.perform(post("/api/v1/daily/today/listen-more?expectedLevel=2").with(auth(user, false)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertNoAnswer(opened);
    assertThat(body(opened).path("listenSeconds").asInt()).isEqualTo(5);
    guessHttp(user, false, COVER.id());
    var result =
        body(
            mvc.perform(get("/api/v1/daily/result").with(auth(user, false)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(result.path("rank").asInt()).isEqualTo(1);
    var state =
        body(
            mvc.perform(get("/api/v1/daily/today").with(auth(user, false)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(state.path("status").asText()).isEqualTo("SOLVED");
    var board =
        body(
            mvc.perform(get("/api/v1/leaderboards/daily").with(auth(user, false)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(board.path("myEntry").path("rank").asInt()).isEqualTo(1);
    var history =
        body(
            mvc.perform(get("/api/v1/daily/history").with(auth(user, false)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(history.path("items").get(0).path("level").asInt()).isEqualTo(5);
  }

  @Autowired com.meloo.daily.web.DailyRateLimiter limiter;

  @Test
  void excessiveRequestsReturn429WithRetryAfterBeforeCreatingAnAttempt() throws Exception {
    UUID user = user(false);
    for (int i = 0; i < 120; i++) {
      limiter.check(user);
    }
    var response =
        mvc.perform(post("/api/v1/daily/today/start").with(auth(user, false)))
            .andExpect(status().isTooManyRequests())
            .andReturn()
            .getResponse();
    assertThat(Long.parseLong(response.getHeader("Retry-After"))).isGreaterThanOrEqualTo(1);
    assertThat(body(response.getContentAsString()).path("code").asText()).isEqualTo("RATE_LIMITED");
    assertThat(attempts.findByUserIdOrderByChallengeDateAsc(user)).isEmpty();
  }

  @Test
  void guestMergeKeepsAccountSameDayAndTransfersOtherDatesIdempotently() {
    UUID guest = user(true);
    UUID account = user(false);
    var accountAttempt = saved(account, false, TODAY, AttemptStatus.UNSOLVED, null, 1000);
    saved(guest, true, TODAY, AttemptStatus.SOLVED, 1, 500);
    saved(guest, true, TODAY.minusDays(1), AttemptStatus.SOLVED, 2, 500);
    var event = new GuestMerged(UUID.randomUUID(), guest, account, clock.instant());
    operations.merge(event);
    operations.merge(event);
    assertThat(attempts.findByUserIdOrderByChallengeDateAsc(guest)).isEmpty();
    assertThat(attempts.findByUserIdOrderByChallengeDateAsc(account)).hasSize(2);
    assertThat(attempts.findByUserIdAndChallengeDate(account, TODAY).orElseThrow().getId())
        .isEqualTo(accountAttempt.getId());
    assertThat(daily.history(account, null, null).getItems())
        .extracting(i -> i.getDate())
        .containsExactly(TODAY.minusDays(1));
    assertCode(() -> daily.start(guest, true), "FORBIDDEN");
  }

  @Test
  void durableGuestMergedListenerTransfersActiveAttemptAndAllowsRegisteredResume() {
    UUID guest = user(true);
    UUID account = user(false);
    daily.start(guest, true);
    daily.listen(guest, 2);
    UUID attemptId = attempts.findByUserIdAndChallengeDate(guest, TODAY).orElseThrow().getId();
    // Real application-module listener runs asynchronously after the event publication transaction.
    new org.springframework.transaction.support.TransactionTemplate(transactions)
        .executeWithoutResult(
            s ->
                publisher.publishEvent(
                    new GuestMerged(UUID.randomUUID(), guest, account, clock.instant())));
    org.awaitility.Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> assertThat(attempts.findByUserIdAndChallengeDate(account, TODAY)).isPresent());
    assertThat(daily.start(account, false).getLevel()).isEqualTo(2);
    assertThat(attempts.findByUserIdAndChallengeDate(account, TODAY).orElseThrow().getId())
        .isEqualTo(attemptId);
    assertThat(daily.guess(account, TARGET.id()).getResult().getRank()).isEqualTo(1);
  }

  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

  @Test
  void leaderboardFiltersBeforeRankingAndPagingAndReturnsOwnEntryOutsidePage() {
    UUID best = user(false),
        slow = user(false),
        late = user(false),
        unsolved = user(false),
        guest = user(true),
        locked = user(false);
    saved(slow, false, TODAY, AttemptStatus.SOLVED, 1, 2000);
    saved(late, false, TODAY, AttemptStatus.SOLVED, 2, 100);
    saved(best, false, TODAY, AttemptStatus.SOLVED, 1, 1000);
    saved(unsolved, false, TODAY, AttemptStatus.UNSOLVED, null, 100);
    saved(guest, true, TODAY, AttemptStatus.SOLVED, 1, 1);
    saved(locked, false, TODAY, AttemptStatus.SOLVED, 1, 1);
    jdbc.update("update app_user set status = 'LOCKED', lock_reason = 'test' where id = ?", locked);
    jdbc.update("update app_user set avatar_id = 'radio' where id = ?", best);
    var page = daily.leaderboard(late, false, null, 0, 2);
    assertThat(page.getItems()).extracting(i -> i.getUserId()).containsExactly(best, slow);
    assertThat(page.getItems()).extracting(i -> i.getRank()).containsExactly(1, 2);
    assertThat(page.getItems().getFirst().getAvatarId().getValue()).isEqualTo("radio");
    assertThat(page.getMyEntry().getRank()).isEqualTo(3);
    assertThat(page.getTotalItems()).isEqualTo(4);
    assertThat(daily.leaderboard(guest, true, null, 0, 10).getMyEntry()).isNull();
    assertThat(daily.leaderboard(best, false, null, 1, 2).getItems())
        .extracting(i -> i.getUserId())
        .containsExactly(late, unsolved);
  }

  @Test
  void concurrentStartsHaveOneAttemptAndConcurrentCorrectGuessesPublishOnce() throws Exception {
    UUID user = user(false);
    try (var pool = Executors.newFixedThreadPool(6)) {
      var starts = new ArrayList<java.util.concurrent.Callable<Integer>>();
      for (int i = 0; i < 6; i++) {
        starts.add(() -> daily.start(user, false).getLevel());
      }
      for (var result : pool.invokeAll(starts)) {
        assertThat(result.get()).isEqualTo(1);
      }
      assertThat(attempts.findByUserIdOrderByChallengeDateAsc(user)).hasSize(1);
      var guesses = new ArrayList<java.util.concurrent.Callable<Boolean>>();
      for (int i = 0; i < 6; i++) {
        guesses.add(
            () -> {
              try {
                return daily.guess(user, TARGET.id()).getCorrect();
              } catch (ApiException ex) {
                assertThat(ex.code()).isEqualTo("ROUND_NOT_ACTIVE");
                return false;
              }
            });
      }
      long correct = 0;
      for (var result : pool.invokeAll(guesses)) {
        if (result.get()) {
          correct++;
        }
      }
      assertThat(correct).isEqualTo(1);
    }
    assertThat(events.finished).hasSize(1);
  }

  private UUID user(boolean guest) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into app_user(id, kind, nickname) values (?, ?, 'Player')",
        id,
        guest ? "GUEST" : "REGISTERED");
    return id;
  }

  private DailyAttempt saved(
      UUID user, boolean guest, LocalDate date, AttemptStatus status, Integer level, int elapsed) {
    jdbc.update(
        "insert into daily_challenge(challenge_date, track_id, source) values (?, ?, 'AUTO') on conflict do nothing",
        date,
        TARGET.id());
    return attempts.saveAndFlush(
        DailyAttempt.builder()
            .id(UUID.randomUUID())
            .userId(user)
            .guest(guest)
            .challengeDate(date)
            .status(status)
            .startedAt(date.atTime(12, 0).toInstant(ZoneOffset.UTC))
            .finishedAt(date.atTime(12, 0).toInstant(ZoneOffset.UTC).plusMillis(elapsed))
            .correctLevel(level == null ? null : level.shortValue())
            .currentLevel(level == null ? (short) 5 : level.shortValue())
            .elapsedMs(elapsed)
            .outcome(status == AttemptStatus.SOLVED ? "CORRECT" : "WRONG_OUT")
            .trackSnapshot(mapper.snapshot(TARGET))
            .build());
  }

  private String guessHttp(UUID user, boolean guest, UUID track) throws Exception {
    return mvc.perform(
            post("/api/v1/daily/today/guesses")
                .with(auth(user, guest))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackId\":\"" + track + "\"}"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor auth(
      UUID user, boolean guest) {
    return jwt().jwt(j -> j.subject(user.toString()).claim("kind", guest ? "GUEST" : "REGISTERED"));
  }

  private JsonNode body(String response) {
    return json.readTree(response);
  }

  private void assertNoAnswer(String response) {
    assertThat(response)
        .doesNotContain(
            "\"answer\"",
            "\"trackId\"",
            "\"artistName\"",
            "\"title\"",
            TARGET.title(),
            TARGET.id().toString());
  }

  private void assertCode(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.code()).isEqualTo(code));
  }

  private static TrackInfo track(String title, String artist) {
    return trackWithId(UUID.randomUUID(), title, artist);
  }

  private static TrackInfo trackWithId(UUID id, String title, String artist) {
    return new TrackInfo(id, title, artist, "DEEZER", "123456", null, null, null, null);
  }
}
