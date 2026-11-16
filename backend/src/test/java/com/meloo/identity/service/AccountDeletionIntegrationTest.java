package com.meloo.identity.service;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.meloo.IntegrationTest;
import com.meloo.admin.analytics.AnalyticsWriter;
import com.meloo.daily.service.DailyOperations;
import com.meloo.game.events.*;
import com.meloo.identity.api.DeletedAccounts;
import com.meloo.identity.events.AccountDeleted;
import com.meloo.identity.events.GuestMerged;
import com.meloo.identity.persistence.*;
import com.meloo.progression.service.ProgressionWriter;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class AccountDeletionIntegrationTest extends IntegrationTest {
  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("MELOO_JWT_SECRET", () -> "test-meloo-deletion-key-at-least-32-bytes");
  }

  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired AuthIdentityRepository identities;
  @Autowired RefreshTokenRepository tokens;
  @Autowired RefreshTokenService refresh;
  @Autowired JwtTokenService jwt;
  @Autowired AccountDeletionService deletion;
  @Autowired InactiveGuestCleanup cleanup;
  @Autowired JdbcTemplate jdbc;
  @Autowired ApplicationEventPublisher events;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  @Autowired ProgressionWriter progression;
  @Autowired AnalyticsWriter analytics;
  @Autowired DailyOperations daily;
  @Autowired DeletedAccounts deletedAccounts;
  @Autowired Clock clock;

  @Test
  void selfDeletionErasesPersonalDataAndSessionsWhileKeepingOtherPlayersHistoryAndRank()
      throws Exception {
    AppUser erased = account(UserKind.REGISTERED);
    AppUser survivor = account(UserKind.REGISTERED);
    var oldSession = refresh.createRefreshToken(erased.getId());
    var rotated = refresh.rotateRefreshToken(oldSession.rawToken());
    identities.saveAndFlush(
        AuthIdentity.builder()
            .id(UUID.randomUUID())
            .userId(erased.getId())
            .provider(AuthProvider.FACEBOOK)
            .providerSubject(UUID.randomUUID().toString())
            .createdAt(clock.instant())
            .build());
    MatchFinished match = match(erased, survivor);
    progression.record(match);
    long survivorRank =
        count("select ranked_score from player_stats where user_id = ?", survivor.getId());
    UUID analyticId = UUID.randomUUID();
    analytics.record(
        analyticId,
        "USER_REGISTERED",
        erased.getId(),
        clock.instant(),
        Map.of("userId", erased.getId(), "players", List.of(erased.getId(), survivor.getId())));
    UUID report = UUID.randomUUID();
    jdbc.update(
        "insert into chat_report(id,message_text,sender_id,sender_nickname,reporter_id,context,handled_by) values (?,'personal text',?,'Personal name',?,cast(? as jsonb),?)",
        report,
        erased.getId(),
        survivor.getId(),
        "[{\"senderId\":\""
            + erased.getId()
            + "\",\"content\":\"personal context\"},{\"senderId\":\""
            + survivor.getId()
            + "\",\"content\":\"keep context\"}]",
        erased.getId());
    LocalDate date = LocalDate.of(2098, 1, 1);
    jdbc.update(
        "insert into daily_challenge(challenge_date,track_id,source,set_by) values (?,?,'AUTO',?) on conflict do nothing",
        date,
        UUID.randomUUID(),
        erased.getId());
    UUID attempt = UUID.randomUUID();
    jdbc.update(
        "insert into daily_attempt(id,challenge_date,user_id,is_guest,status,started_at) values (?,?,?,false,'IN_PROGRESS',now())",
        attempt,
        date,
        erased.getId());
    jdbc.update(
        "insert into user_achievement(user_id,achievement_code,unlocked_at) values (?,'FIRST_WIN',now())",
        erased.getId());
    String bearer = jwt.generateAccessToken(erased).token();
    mvc.perform(delete("/api/v1/me").header("Authorization", "Bearer " + bearer))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("meloo_refresh", 0));
    AppUser tombstone = users.findById(erased.getId()).orElseThrow();
    assertThat(tombstone.getEmail()).isNull();
    assertThat(tombstone.getAvatarId()).isNull();
    assertThat(tombstone.getNickname()).isEqualTo("Deleted user");
    assertThat(tombstone.getStatus()).isEqualTo(UserStatus.LOCKED);
    assertThat(tombstone.getMergedIntoId()).isEqualTo(tombstone.getId());
    assertThat(identities.findAllByUserId(erased.getId())).isEmpty();
    assertThat(tokens.findByTokenHash(RefreshTokenService.hashToken(rotated.rawToken()))).isEmpty();
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + bearer))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("meloo_refresh", rotated.rawToken())))
        .andExpect(status().isUnauthorized());
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(
                      count("select count(*) from player_stats where user_id = ?", erased.getId()))
                  .isZero();
              assertThat(
                      count(
                          "select count(*) from user_achievement where user_id = ?",
                          erased.getId()))
                  .isZero();
              assertThat(
                      count(
                          "select count(*) from match_player where match_id = ? and user_id is null and owner_id is null and nickname = 'Deleted user'",
                          match.matchId()))
                  .isEqualTo(1);
              assertThat(
                      count(
                          "select count(*) from analytics_event where props ->> 'eventId' = ? and user_id is null and not jsonb_exists(props, 'userId') and not jsonb_exists(props -> 'players', ?)",
                          analyticId.toString(),
                          erased.getId().toString()))
                  .isEqualTo(1);
              assertThat(
                      count(
                          "select count(*) from daily_attempt where id = ? and user_id is null and status = 'UNSOLVED'",
                          attempt))
                  .isEqualTo(1);
              assertThat(
                      jdbc.queryForObject(
                          "select sender_nickname from chat_report where id = ?",
                          String.class,
                          report))
                  .isEqualTo("Deleted user");
              assertThat(
                      jdbc.queryForObject(
                          "select context::text from chat_report where id = ?",
                          String.class,
                          report))
                  .contains("keep context")
                  .doesNotContain(erased.getId().toString(), "personal context");
            });
    assertThat(count("select ranked_score from player_stats where user_id = ?", survivor.getId()))
        .isEqualTo(survivorRank);
    assertThat(
            count(
                "select count(*) from match_player where match_id = ? and user_id = ? and owner_id = ?",
                match.matchId(),
                survivor.getId(),
                survivor.getId()))
        .isEqualTo(1);
    // Exercise registry redelivery after the first delivery completed.
    new TransactionTemplate(transactions)
        .executeWithoutResult(s -> events.publishEvent(new AccountDeleted(erased.getId())));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        count(
                            "select count(*) from event_publication where event_type = ? and completion_date is null",
                            AccountDeleted.class.getName()))
                    .isZero());
    assertThat(
            count(
                "select count(*) from analytics_event where props ->> 'eventId' = ?",
                analyticId.toString()))
        .isEqualTo(1);
  }

  @Test
  void guestsAndUnauthenticatedCallersCannotSelfDelete() throws Exception {
    AppUser guest = account(UserKind.GUEST);
    mvc.perform(delete("/api/v1/me")).andExpect(status().isUnauthorized());
    mvc.perform(
            delete("/api/v1/me")
                .header("Authorization", "Bearer " + jwt.generateAccessToken(guest).token()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("GUEST_CANNOT_DELETE_ACCOUNT"));
    assertThat(users.findById(guest.getId()).orElseThrow().getEmail()).isNotNull();
  }

  @Test
  void cleanupDeletesOnlyGuestsStrictlyOlderThanThirtyDaysAndRechecksActivity() {
    Instant cutoff =
        clock
            .instant()
            .minus(Duration.ofDays(30))
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    AppUser old = account(UserKind.GUEST);
    AppUser recent = account(UserKind.GUEST);
    AppUser registered = account(UserKind.REGISTERED);
    activity(old, cutoff.minusSeconds(10));
    activity(recent, clock.instant());
    activity(registered, cutoff.minusSeconds(100));
    cleanup.run();
    assertThat(users.findById(old.getId()).orElseThrow().getMergedIntoId()).isEqualTo(old.getId());
    assertThat(users.findById(recent.getId()).orElseThrow().getMergedIntoId()).isNull();
    assertThat(users.findById(registered.getId()).orElseThrow().getMergedIntoId()).isNull();
    AppUser boundary = account(UserKind.GUEST);
    activity(boundary, cutoff);
    deletion.deleteInactiveGuest(boundary.getId(), cutoff);
    assertThat(users.findById(boundary.getId()).orElseThrow().getMergedIntoId()).isNull();
    activity(boundary, cutoff.minusNanos(1000));
    deletion.deleteInactiveGuest(boundary.getId(), cutoff);
    assertThat(users.findById(boundary.getId()).orElseThrow().getMergedIntoId())
        .isEqualTo(boundary.getId());
    deletion.deleteInactiveGuest(recent.getId(), cutoff);
    assertThat(users.findById(recent.getId()).orElseThrow().getMergedIntoId()).isNull();
  }

  @Test
  void lateMatchMergeAndAnalyticsCannotResurrectDeletedData() {
    AppUser erased = account(UserKind.REGISTERED);
    AppUser survivor = account(UserKind.REGISTERED);
    AppUser guest = account(UserKind.GUEST);
    deletion.deleteMyAccount(erased.getId());
    MatchFinished late = match(erased, survivor);
    progression.record(late);
    assertThat(
            count(
                "select count(*) from match_player where match_id = ? and nickname = 'Deleted user' and user_id is null and owner_id is null",
                late.matchId()))
        .isEqualTo(1);
    assertThat(count("select count(*) from player_stats where user_id = ?", erased.getId()))
        .isZero();
    GuestMerged merge =
        new GuestMerged(UUID.randomUUID(), guest.getId(), erased.getId(), clock.instant());
    progression.merge(merge);
    daily.merge(merge);
    assertThat(
            count("select count(*) from progression_guest_merge where guest_id = ?", guest.getId()))
        .isZero();
    assertThat(count("select count(*) from daily_guest_merge where guest_id = ?", guest.getId()))
        .isZero();
    UUID event = UUID.randomUUID();
    analytics.record(
        event,
        "USER_REGISTERED",
        erased.getId(),
        clock.instant(),
        Map.of("userId", erased.getId(), "players", List.of(erased.getId(), survivor.getId())));
    assertThat(
            count(
                "select count(*) from analytics_event where props ->> 'eventId' = ? and user_id is null",
                event.toString()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select props::text from analytics_event where props ->> 'eventId' = ?",
                String.class,
                event.toString()))
        .doesNotContain(erased.getId().toString())
        .contains(survivor.getId().toString());
  }

  @Test
  void sharedGuardBlocksDeletionUntilWriterTransactionCommitsAndMissingAccountsAreNotDeleted()
      throws Exception {
    AppUser user = account(UserKind.REGISTERED);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<?> writer =
          executor.submit(
              () ->
                  new TransactionTemplate(transactions)
                      .executeWithoutResult(
                          s -> {
                            assertThat(
                                    deletedAccounts.findDeleted(
                                        List.of(user.getId(), UUID.randomUUID())))
                                .isEmpty();
                            entered.countDown();
                            try {
                              if (!release.await(10, TimeUnit.SECONDS))
                                throw new AssertionError("release timeout");
                            } catch (InterruptedException e) {
                              Thread.currentThread().interrupt();
                              throw new AssertionError(e);
                            }
                          }));
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      Future<?> deleting = executor.submit(() -> deletion.deleteMyAccount(user.getId()));
      try {
        assertThatThrownBy(() -> deleting.get(200, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
      } finally {
        release.countDown();
      }
      writer.get(10, TimeUnit.SECONDS);
      deleting.get(10, TimeUnit.SECONDS);
    }
    Set<UUID> deleted =
        new TransactionTemplate(transactions)
            .execute(s -> deletedAccounts.findDeleted(List.of(user.getId())));
    assertThat(deleted).containsExactly(user.getId());
  }

  private AppUser account(UserKind kind) {
    return users.saveAndFlush(
        AppUser.builder()
            .id(UUID.randomUUID())
            .kind(kind)
            .nickname("PersonalName")
            .email(UUID.randomUUID() + "@example.com")
            .avatarId("piano")
            .build());
  }

  private void activity(AppUser user, Instant at) {
    jdbc.update(
        "update app_user set last_active_at = ? where id = ?", Timestamp.from(at), user.getId());
  }

  private long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  private MatchFinished match(AppUser one, AppUser two) {
    return new MatchFinished(
        UUID.randomUUID(),
        UUID.randomUUID(),
        MatchType.QUICK_MATCH,
        "RANKED",
        true,
        MatchEndStatus.FINISHED,
        5,
        null,
        null,
        clock.instant().minusSeconds(60),
        clock.instant(),
        List.of(),
        List.of(
            new PlayerSummary(one.getId(), one.getNickname(), false, 10, 1, 0, 0, List.of()),
            new PlayerSummary(two.getId(), two.getNickname(), false, 5, 2, 0, 0, List.of())));
  }
}
