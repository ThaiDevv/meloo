package com.meloo.progression.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.meloo.IntegrationTest;
import com.meloo.daily.events.DailyAttemptFinished;
import com.meloo.game.events.*;
import com.meloo.identity.events.AccountDeleted;
import com.meloo.identity.events.GuestMerged;
import com.meloo.progression.events.AchievementUnlocked;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@RecordApplicationEvents
@Transactional
class AchievementIntegrationTest extends IntegrationTest {
  @Autowired AchievementService service;
  @Autowired AchievementQueries queries;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired ApplicationEvents events;
  @Autowired ApplicationEventPublisher publisher;
  @Autowired PlatformTransactionManager transactions;

  @ParameterizedTest
  @EnumSource(
      value = MatchType.class,
      names = {"SOLO", "PRIVATE_ROOM", "PUBLIC_ROOM"})
  void unlocksAtExactThresholdInEveryUnrankedModeAndReplayDoesNotReemit(MatchType type) {
    UUID user = account();
    var first = match(user, type, 5, 1, 1, 0);
    service.matchFinished(first);
    assertThat(unlocked(user))
        .contains("FIRST_WIN", "LIGHTNING_EAR", "COMBO_5")
        .doesNotContain("SHARP_EAR_10", "UNBEATEN_5");
    var next = match(user, type, 4, 1, 1, 1);
    service.matchFinished(next);
    assertThat(unlocked(user)).doesNotContain("SHARP_EAR_10");
    var threshold = match(user, type, 1, 1, 1, 2);
    service.matchFinished(threshold);
    assertThat(unlocked(user)).contains("SHARP_EAR_10");
    long count =
        events.stream(AchievementUnlocked.class).filter(e -> e.userId().equals(user)).count();
    service.matchFinished(threshold);
    // Same match business key with a fresh publication id must still count once.
    service.matchFinished(
        new MatchFinished(
            UUID.randomUUID(),
            threshold.matchId(),
            threshold.type(),
            threshold.mode(),
            false,
            threshold.status(),
            5,
            null,
            null,
            threshold.startedAt(),
            threshold.endedAt(),
            threshold.rounds(),
            threshold.players()));
    assertThat(
            events.stream(AchievementUnlocked.class).filter(e -> e.userId().equals(user)).count())
        .isEqualTo(count);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from achievement_fact where owner_id=?", Integer.class, user))
        .isEqualTo(3);
  }

  @Test
  void rulesAreDataDrivenAndFinishWinAndCorrectThresholdsAreExact() {
    UUID user = account();
    for (int i = 0; i < 4; i++) service.matchFinished(match(user, MatchType.SOLO, 5, 3, 1, i));
    assertThat(unlocked(user)).doesNotContain("UNBEATEN_5");
    service.matchFinished(match(user, MatchType.SOLO, 5, 3, 1, 4));
    assertThat(unlocked(user)).contains("UNBEATEN_5");
    for (int i = 5; i < 99; i++) {
      service.matchFinished(match(user, MatchType.SOLO, 5, 3, 2, i));
      if (i == 8) assertThat(unlocked(user)).doesNotContain("MATCHES_10");
      if (i == 9) assertThat(unlocked(user)).contains("MATCHES_10");
      if (i == 18) assertThat(unlocked(user)).doesNotContain("CORRECT_100");
      if (i == 19) assertThat(unlocked(user)).contains("CORRECT_100");
    }
    assertThat(unlocked(user)).doesNotContain("MATCHES_100", "CORRECT_500");
    jdbc.update(
        """
        insert into achievement(code,sort_order,name_vi,name_en,description_vi,description_en,metric,threshold)
        values ('CUSTOM_MATCH_100',11,'Mới','New','Mới','New','MATCHES',100)
        """);
    service.matchFinished(match(user, MatchType.SOLO, 5, 3, 2, 99));
    assertThat(unlocked(user))
        .contains("MATCHES_100", "CORRECT_500", "CUSTOM_MATCH_100", "UNBEATEN_5");
  }

  @Test
  void dailyWinsUseChallengeDateWithGapsLossesOutOfOrderAndReplay() {
    UUID user = account();
    for (int day : new int[] {1, 2, 3, 5, 6, 7}) service.dailyFinished(daily(user, day, true));
    assertThat(unlocked(user)).doesNotContain("DAILY_STREAK_7");
    var missing = daily(user, 4, true);
    service.dailyFinished(missing);
    assertThat(unlocked(user)).contains("DAILY_STREAK_7");
    var at = unlockedAt(user, "DAILY_STREAK_7");
    service.dailyFinished(missing);
    service.dailyFinished(daily(user, 8, false));
    assertThat(unlockedAt(user, "DAILY_STREAK_7")).isEqualTo(at);
    assertThat(
            events.stream(AchievementUnlocked.class)
                .filter(e -> e.userId().equals(user) && e.code().equals("DAILY_STREAK_7"))
                .count())
        .isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void mergeKeepsEarliestUnlockIsIdempotentAndLateGuestEventsAccrueToAccount(boolean guestEarlier) {
    UUID guest = account(), user = account();
    service.matchFinished(match(guest, MatchType.SOLO, 5, 1, 1, 0));
    service.matchFinished(match(user, MatchType.SOLO, 4, 1, 1, 1));
    Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
    jdbc.update(
        "update user_achievement set unlocked_at=? where user_id=?",
        java.sql.Timestamp.from(earlier),
        guestEarlier ? guest : user);
    var merge = new GuestMerged(UUID.randomUUID(), guest, user, Instant.now());
    service.guestMerged(merge);
    service.guestMerged(merge);
    assertThat(unlocked(guest)).isEmpty();
    assertThat(unlockedAt(user, "FIRST_WIN")).isEqualTo(earlier);
    service.matchFinished(match(guest, MatchType.SOLO, 1, 1, 1, 2));
    assertThat(unlocked(user)).contains("SHARP_EAR_10");
    assertThat(unlocked(guest)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from achievement_fact where owner_id=?", Integer.class, guest))
        .isZero();
  }

  @Test
  void mergedDailyCollisionsPreferAccountEvenIfGuestEventsArriveLate() {
    UUID guest = account(), user = account();
    service.dailyFinished(daily(user, 4, false));
    service.guestMerged(new GuestMerged(UUID.randomUUID(), guest, user, Instant.now()));
    for (int day = 1; day <= 7; day++) service.dailyFinished(daily(guest, day, true));
    assertThat(unlocked(user)).doesNotContain("DAILY_STREAK_7");
  }

  @Test
  void apiUsesGeneratedShapeCatalogOrderCallerIsolationAndAuthentication() throws Exception {
    UUID user = account(), other = account();
    service.matchFinished(match(user, MatchType.SOLO, 5, 1, 1, 0));
    mvc.perform(get("/api/v1/me/achievements")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me/achievements").with(jwt().jwt(j -> j.subject(user.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCount").value(10))
        .andExpect(jsonPath("$.unlockedCount").value(3))
        .andExpect(jsonPath("$.items[0].code").value("FIRST_WIN"))
        .andExpect(jsonPath("$.items[0].nameVi").isString())
        .andExpect(jsonPath("$.items[0].nameEn").isString())
        .andExpect(jsonPath("$.items[0].unlockedAt").isString())
        .andExpect(jsonPath("$.items[9].unlocked").value(false));
    assertThat(queries.list(other).getUnlockedCount()).isZero();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void committedSourceEventsReachBothListenersAndConcurrentReplaysUnlockOnce() throws Exception {
    UUID user = account();
    var match = match(user, MatchType.PRIVATE_ROOM, 5, 1, 1, 1);
    new TransactionTemplate(transactions).executeWithoutResult(tx -> publisher.publishEvent(match));
    await()
        .atMost(java.time.Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              assertThat(unlocked(user)).contains("FIRST_WIN", "LIGHTNING_EAR", "COMBO_5");
              assertThat(
                      jdbc.queryForObject(
                          "select count(*) from match_player where owner_id=?",
                          Integer.class,
                          user))
                  .isEqualTo(1);
            });
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var a = executor.submit(() -> service.matchFinished(match));
      var b = executor.submit(() -> service.matchFinished(match));
      a.get(10, java.util.concurrent.TimeUnit.SECONDS);
      b.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from achievement_fact where owner_id=?", Integer.class, user))
        .isEqualTo(1);
    assertThat(unlocked(user)).hasSize(3);
    for (int day = 1; day <= 7; day++) {
      var daily = daily(user, day, true);
      new TransactionTemplate(transactions)
          .executeWithoutResult(tx -> publisher.publishEvent(daily));
    }
    await()
        .atMost(java.time.Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(unlocked(user)).contains("DAILY_STREAK_7"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from achievement_fact where owner_id=?", Integer.class, user))
        .isEqualTo(8);
  }

  @Test
  void lateMatchDailyAndMergeSkipDeletedSourceAndOwnerWhilePreservingOthers() {
    UUID deleted = account(), live = account(), guest = account();
    tombstone(deleted);
    var original = match(deleted, MatchType.PRIVATE_ROOM, 5, 1, 1, 1);
    var livePlayer = match(live, MatchType.PRIVATE_ROOM, 5, 1, 1, 1).players().getFirst();
    service.matchFinished(
        new MatchFinished(
            original.eventId(),
            original.matchId(),
            original.type(),
            original.mode(),
            false,
            original.status(),
            5,
            null,
            null,
            original.startedAt(),
            original.endedAt(),
            original.rounds(),
            List.of(original.players().getFirst(), livePlayer)));
    service.dailyFinished(daily(deleted, 1, true));
    service.guestMerged(new GuestMerged(UUID.randomUUID(), deleted, live, Instant.now()));
    service.guestMerged(new GuestMerged(UUID.randomUUID(), guest, deleted, Instant.now()));
    assertThat(unlocked(deleted)).isEmpty();
    assertThat(unlocked(live)).hasSize(3);
    assertThat(facts(deleted)).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from progression_guest_merge where guest_id=?",
                Integer.class,
                guest))
        .isZero();
    jdbc.update(
        "insert into progression_guest_merge(guest_id,user_id) values (?,?)", guest, deleted);
    service.matchFinished(match(guest, MatchType.SOLO, 5, 1, 1, 2));
    service.dailyFinished(daily(guest, 2, true));
    assertThat(facts(deleted)).isZero();
    assertThat(unlocked(guest)).isEmpty();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void committedDeletionScrubsFactsAndUnlocksIdempotentlyThenRejectsLateReplay() {
    UUID user = account();
    var source = match(user, MatchType.SOLO, 5, 1, 1, 1);
    service.matchFinished(source);
    service.dailyFinished(daily(user, 1, true));
    assertThat(facts(user)).isEqualTo(2);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              tombstone(user);
              publisher.publishEvent(new AccountDeleted(user));
            });
    await()
        .atMost(java.time.Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              assertThat(facts(user)).isZero();
              assertThat(unlocked(user)).isEmpty();
            });
    service.accountDeleted(new AccountDeleted(user));
    service.matchFinished(source);
    service.dailyFinished(daily(user, 2, true));
    assertThat(facts(user)).isZero();
    assertThat(unlocked(user)).isEmpty();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void deletionWaitsForInFlightAchievementWriteThenScrubsIt() throws Exception {
    UUID user = account();
    var written = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var write =
          executor.submit(
              () ->
                  new TransactionTemplate(transactions)
                      .executeWithoutResult(
                          tx -> {
                            service.matchFinished(match(user, MatchType.SOLO, 5, 1, 1, 1));
                            written.countDown();
                            try {
                              if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                                throw new IllegalStateException("Release timed out");
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                              throw new IllegalStateException(e);
                            }
                          }));
      try {
        assertThat(written.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        var deletion =
            executor.submit(
                () ->
                    new TransactionTemplate(transactions)
                        .executeWithoutResult(
                            tx -> {
                              jdbc.queryForList(
                                  "select pg_advisory_xact_lock(hashtextextended(?,84))",
                                  user.toString());
                              tombstone(user);
                              publisher.publishEvent(new AccountDeleted(user));
                            }));
        await()
            .atMost(java.time.Duration.ofSeconds(5))
            .until(
                () ->
                    jdbc.queryForObject(
                        "select exists(select 1 from pg_locks where locktype='advisory' and not granted)",
                        Boolean.class));
        release.countDown();
        write.get(10, java.util.concurrent.TimeUnit.SECONDS);
        deletion.get(10, java.util.concurrent.TimeUnit.SECONDS);
      } finally {
        release.countDown();
      }
    }
    await()
        .atMost(java.time.Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              assertThat(facts(user)).isZero();
              assertThat(unlocked(user)).isEmpty();
            });
  }

  private void tombstone(UUID user) {
    jdbc.update(
        "update app_user set merged_into_id=id,status='LOCKED',lock_reason='Account deleted' where id=?",
        user);
  }

  private int facts(UUID user) {
    return jdbc.queryForObject(
        "select count(*) from achievement_fact where owner_id=?", Integer.class, user);
  }

  private List<String> unlocked(UUID user) {
    return jdbc.queryForList(
        "select achievement_code from user_achievement where user_id=?", String.class, user);
  }

  private Instant unlockedAt(UUID user, String code) {
    return jdbc.queryForObject(
            "select unlocked_at from user_achievement where user_id=? and achievement_code=?",
            java.sql.Timestamp.class,
            user,
            code)
        .toInstant();
  }

  private UUID account() {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into app_user(id,kind,nickname) values (?,'GUEST','Player')", id);
    return id;
  }

  private MatchFinished match(
      UUID user, MatchType type, int correct, int level, int rank, int seconds) {
    var results =
        IntStream.rangeClosed(1, correct)
            .mapToObj(i -> AchievementMetricsTest.round(i, level))
            .toList();
    var rounds =
        IntStream.rangeClosed(1, correct)
            .mapToObj(
                i -> new RoundSummary(i, UUID.randomUUID(), "Title", "Artist", "DEEZER", "123"))
            .toList();
    return new MatchFinished(
        UUID.randomUUID(),
        UUID.randomUUID(),
        type,
        "PRACTICE",
        false,
        MatchEndStatus.FINISHED,
        5,
        null,
        null,
        Instant.EPOCH,
        Instant.EPOCH.plusSeconds(seconds),
        rounds,
        List.of(
            new PlayerSummary(user, "Player", true, correct * 100, rank, correct, 100L, results)));
  }

  private DailyAttemptFinished daily(UUID user, int day, boolean won) {
    UUID id = UUID.randomUUID();
    return new DailyAttemptFinished(
        id,
        id,
        user,
        true,
        LocalDate.of(2026, 1, day),
        won ? "SOLVED" : "UNSOLVED",
        won ? "CORRECT" : "TIMEOUT",
        won ? 1 : null,
        won ? 1000 : 0,
        100,
        Instant.EPOCH.plusSeconds(day));
  }
}
