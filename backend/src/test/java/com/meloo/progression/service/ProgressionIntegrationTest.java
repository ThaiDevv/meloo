package com.meloo.progression.service;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.meloo.IntegrationTest;
import com.meloo.game.domain.RoundOutcome;
import com.meloo.game.events.*;
import com.meloo.identity.events.GuestMerged;
import com.meloo.room.api.RoomDescriptor;
import com.meloo.room.realtime.RoomRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Transactional
class ProgressionIntegrationTest extends IntegrationTest {
  @Autowired ProgressionWriter writer;
  @Autowired ProgressionQueries queries;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired ApplicationEventPublisher events;
  @Autowired PlatformTransactionManager transactions;
  @Autowired RoomRegistry rooms;
  @Autowired ObjectMapper mapper;

  @Autowired
  @Qualifier("brokerChannel") AbstractSubscribableChannel broker;

  @BeforeEach
  void isolateProgression() {
    jdbc.update("delete from match");
    jdbc.update("delete from player_stats");
    jdbc.update("delete from progression_guest_merge");
  }

  @ParameterizedTest
  @EnumSource(
      value = MatchType.class,
      names = {"SOLO", "PRIVATE_ROOM", "PUBLIC_ROOM"})
  void unrankedModesKeepHistoryStatsXpAndSnapshotsEvenWhenRankedFlagIsTrue(MatchType type) {
    UUID user = account(false);
    var event =
        match(type, MatchEndStatus.FINISHED, true, List.of(player(user, false, 1, false, 3)), null);
    writer.record(event);
    writer.record(event);
    var stats = queries.stats(user);
    assertThat(stats.getMatchesPlayed()).isEqualTo(1);
    assertThat(stats.getXp()).isEqualTo(3000);
    assertThat(stats.getBestCombo()).isEqualTo(3);
    assertThat(stats.getRankPoints()).isZero();
    assertThat(stats.getRoundsPlayed()).isEqualTo(3);
    assertThat(stats.getAverageCorrectLevel()).isEqualTo(1d);
    assertThat(stats.getAverageListenSeconds()).isEqualTo(2d);
    var history = queries.history(user, 0, 10);
    assertThat(history.getItems()).hasSize(1);
    assertThat(history.getItems().getFirst().getRanked()).isFalse();
    assertThat(history.getItems().getFirst().getRankDelta()).isNull();
    var detail = queries.detail(user, event.matchId());
    assertThat(detail.getRounds()).hasSize(3);
    assertThat(detail.getRounds().getFirst().getAnswer().getTitle()).isEqualTo("Snapshot title 1");
    assertThat(detail.getRounds().getFirst().getAnswer().getListenUrl().toString())
        .isEqualTo("https://www.deezer.com/track/track1");
    assertThat(
            jdbc.queryForObject(
                "select mode from match where id = ?", String.class, event.matchId()))
        .isEqualTo(event.mode());
    assertThat(
            jdbc.queryForMap(
                "select provider,provider_track_id from round where match_id = ? and round_no = 1",
                event.matchId()))
        .containsEntry("provider", "DEEZER")
        .containsEntry("provider_track_id", "track1");
    assertThat(queries.leaderboard(user, 0, 10).getItems()).isEmpty();
  }

  @Test
  void abandonedMatchesPersistCompletedRoundsAndXpButNoRankOrWin() {
    UUID user = account(false);
    var event =
        match(
            MatchType.QUICK_MATCH,
            MatchEndStatus.ABANDONED,
            true,
            List.of(player(user, false, 1, false, 2)),
            null);
    writer.record(event);
    var detail = queries.detail(user, event.matchId());
    assertThat(detail.getStatus().getValue()).isEqualTo("ABANDONED");
    assertThat(detail.getRanked()).isFalse();
    assertThat(detail.getRounds()).hasSize(2);
    assertThat(queries.stats(user).getXp()).isEqualTo(2000);
    assertThat(queries.stats(user).getMatchesWon()).isZero();
    assertThat(queries.leaderboard(user, 0, 10).getItems()).isEmpty();
  }

  @Test
  void fivePlayersGetApprovedDeltasAndFloorAndDeparturesAreLast() {
    List<UUID> users = new ArrayList<>();
    List<PlayerSummary> players = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      UUID id = account(false);
      users.add(id);
      jdbc.update(
          "insert into player_stats(user_id,is_guest,ranked_score) values (?,false,100)", id);
      players.add(player(id, false, i + 1, false, 1));
    }
    var event = match(MatchType.QUICK_MATCH, MatchEndStatus.FINISHED, true, players, null);
    writer.record(event);
    writer.record(event);
    assertThat(users.stream().map(id -> queries.stats(id).getRankPoints()).toList())
        .containsExactly(130, 115, 100, 90, 80);
    assertThat(
            jdbc.queryForList(
                "select rank_delta from match_player where match_id = ? order by rank",
                Integer.class,
                event.matchId()))
        .containsExactly(30, 15, 0, -10, -20);
    UUID loser = users.getFirst();
    jdbc.update("update player_stats set ranked_score = 5 where user_id = ?", loser);
    var departure =
        match(
            MatchType.QUICK_MATCH,
            MatchEndStatus.FINISHED,
            true,
            List.of(player(loser, false, 1, true, 1), player(users.get(1), false, 1, false, 1)),
            null);
    writer.record(departure);
    assertThat(queries.detail(loser, departure.matchId()).getRank()).isEqualTo(2);
    assertThat(queries.detail(loser, departure.matchId()).getRankDelta()).isEqualTo(-5);
    assertThat(queries.stats(loser).getRankPoints()).isZero();
  }

  @Test
  void guestMergeTransfersAllStatsAndHistoryExactlyOnceIncludingLateMatchEvents() {
    UUID guest = account(true), user = account(false);
    var first =
        match(
            MatchType.SOLO,
            MatchEndStatus.FINISHED,
            false,
            List.of(player(guest, true, 1, false, 2)),
            null);
    writer.record(first);
    writer.record(
        match(
            MatchType.SOLO,
            MatchEndStatus.FINISHED,
            false,
            List.of(player(user, false, 1, false, 1)),
            null));
    var merge = new GuestMerged(UUID.randomUUID(), guest, user, Instant.now());
    jdbc.update(
        "insert into user_achievement(user_id,achievement_code,unlocked_at) values (?,'FIRST_WIN','2026-01-01T00:00:00Z'),(?,'FIRST_WIN','2026-02-01T00:00:00Z'),(?,'COMBO_5','2026-03-01T00:00:00Z')",
        guest,
        user,
        guest);
    writer.merge(merge);
    writer.merge(new GuestMerged(UUID.randomUUID(), guest, user, Instant.now()));
    writer.record(first);
    writer.record(
        match(
            MatchType.SOLO,
            MatchEndStatus.FINISHED,
            false,
            List.of(player(guest, true, 1, false, 1)),
            null));
    assertThat(queries.history(guest, 0, 10).getItems()).isEmpty();
    assertThat(queries.history(user, 0, 10).getTotalItems()).isEqualTo(3);
    assertThat(queries.stats(user).getMatchesPlayed()).isEqualTo(3);
    assertThat(queries.stats(user).getXp()).isEqualTo(4000);
    assertThat(queries.stats(user).getRoundsCorrect()).isEqualTo(4);
    assertThat(queries.stats(user).getBestCombo()).isEqualTo(2);
    assertThat(queries.stats(guest).getXp()).isZero();
    assertThat(
            jdbc.queryForList(
                "select achievement_code from user_achievement where user_id=?",
                String.class,
                user))
        .containsExactlyInAnyOrder("FIRST_WIN", "COMBO_5");
    assertThat(
            jdbc.queryForObject(
                    "select unlocked_at from user_achievement where user_id=? and achievement_code='FIRST_WIN'",
                    java.sql.Timestamp.class,
                    user)
                .toInstant())
        .isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from user_achievement where user_id=?", Integer.class, guest))
        .isZero();
    // Historical participants remain snapshots, independent of current ownership.
    assertThat(queries.detail(user, first.matchId()).getPlayers().getFirst().getIsGuest()).isTrue();
  }

  @Test
  void leaderboardFiltersEligibilityBeforePagingAndKeepsMyEntryOutsidePage() {
    UUID first = account(false),
        second = account(false),
        locked = account(false),
        guest = account(true);
    var event =
        match(
            MatchType.QUICK_MATCH,
            MatchEndStatus.FINISHED,
            true,
            List.of(
                player(first, false, 1, false, 1),
                player(second, false, 2, false, 1),
                player(locked, false, 3, false, 1)),
            null);
    writer.record(event);
    jdbc.update(
        "update player_stats set ranked_score=20, ranked_reached_at='2026-11-01T00:00:00Z' where user_id=?",
        first);
    jdbc.update(
        "update player_stats set ranked_score=20, ranked_reached_at='2026-11-02T00:00:00Z' where user_id=?",
        second);
    jdbc.update("update player_stats set ranked_score=999 where user_id=?", locked);
    jdbc.update("update app_user set status='LOCKED',lock_reason='Test lock' where id=?", locked);
    jdbc.update("update app_user set nickname='CurrentNickname' where id=?", first);
    var page = queries.leaderboard(second, 0, 1);
    assertThat(page.getTotalItems()).isEqualTo(2);
    assertThat(page.getTotalPages()).isEqualTo(2);
    assertThat(page.getItems()).extracting(e -> e.getUserId()).containsExactly(first);
    assertThat(page.getItems().getFirst().getNickname()).isEqualTo("CurrentNickname");
    assertThat(page.getMyEntry().getRank()).isEqualTo(2);
    assertThat(queries.leaderboard(guest, 5, 1).getMyEntry()).isNull();
    assertThat(queries.leaderboard(first, 5, 1).getItems()).isEmpty();
  }

  @Test
  void restEnforcesAuthOwnershipValidationAndContractFields() throws Exception {
    UUID user = account(false), stranger = account(false);
    var event =
        match(
            MatchType.SOLO,
            MatchEndStatus.FINISHED,
            false,
            List.of(player(user, false, 1, false, 1)),
            null);
    writer.record(event);
    for (String path :
        List.of(
            "/me/matches",
            "/me/stats",
            "/leaderboards/all-time",
            "/me/matches/" + event.matchId())) {
      mvc.perform(get("/api/v1" + path)).andExpect(status().isUnauthorized());
    }
    mvc.perform(
            get("/api/v1/me/matches/" + event.matchId())
                .with(jwt().jwt(j -> j.subject(stranger.toString()))))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/me/matches/" + event.matchId())
                .with(jwt().jwt(j -> j.subject(user.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rounds[0].answer.provider").value("DEEZER"))
        .andExpect(jsonPath("$.players[0].userId").value(user.toString()));
    mvc.perform(get("/api/v1/me/matches").with(jwt().jwt(j -> j.subject(user.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].mode").value("PRACTICE"));
    mvc.perform(get("/api/v1/me/stats").with(jwt().jwt(j -> j.subject(user.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.xp").value(1000))
        .andExpect(jsonPath("$.winRate").value(100d));
    for (String path : List.of("/me/matches", "/leaderboards/all-time")) {
      mvc.perform(get("/api/v1" + path).param("size", "51").with(jwt()))
          .andExpect(status().isBadRequest());
      mvc.perform(get("/api/v1" + path).param("page", "-1").with(jwt()))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void committedPublicationAndConcurrentReplaysOnlyApplyOnce() throws Exception {
    UUID user = account(false), other = account(false);
    UUID room = UUID.randomUUID();
    rooms.register(new RoomDescriptor(room, Set.of(user, other), false));
    CompletableFuture<JsonNode> received = new CompletableFuture<>();
    AtomicInteger notifications = new AtomicInteger();
    var interceptor =
        new ChannelInterceptor() {
          @Override
          public Message<?> preSend(Message<?> message, MessageChannel channel) {
            if (("/topic/rooms/" + room + "/scoreboard")
                .equals(message.getHeaders().get("simpDestination"))) {
              try {
                JsonNode payload =
                    message.getPayload() instanceof byte[] bytes
                        ? mapper.readTree(bytes)
                        : mapper.valueToTree(message.getPayload());
                if ("RANK_UPDATED".equals(payload.path("type").asText())) {
                  assertThat(queries.stats(user).getRankPoints()).isEqualTo(20);
                  notifications.incrementAndGet();
                  received.complete(payload);
                }
              } catch (Throwable failure) {
                received.completeExceptionally(failure);
              }
            }
            return message;
          }
        };
    broker.addInterceptor(interceptor);
    try {
      var event =
          match(
              MatchType.QUICK_MATCH,
              MatchEndStatus.FINISHED,
              true,
              List.of(player(user, false, 1, false, 1), player(other, false, 2, false, 1)),
              room);
      new TransactionTemplate(transactions).executeWithoutResult(tx -> events.publishEvent(event));
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(queries.stats(user).getRankPoints()).isEqualTo(20));
      try (var executor = Executors.newFixedThreadPool(4)) {
        List<CompletableFuture<Void>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
          calls.add(CompletableFuture.runAsync(() -> writer.record(event), executor));
        }
        CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
      }
      assertThat(queries.stats(user).getMatchesPlayed()).isEqualTo(1);
      assertThat(queries.stats(user).getRankPoints()).isEqualTo(20);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from match where id=?", Integer.class, event.matchId()))
          .isEqualTo(1);
      var notification = received.get(10, TimeUnit.SECONDS);
      assertThat(notification.path("matchId").asText()).isEqualTo(event.matchId().toString());
      assertThat(notification.path("changes").get(0).path("rankDelta").asInt()).isEqualTo(20);
      assertThat(notification.path("changes").get(0).path("rankPoints").asInt()).isEqualTo(20);
      assertThat(notification.path("serverTime").asText()).isNotBlank();
      assertThat(notifications).hasValue(1);
    } finally {
      broker.removeInterceptor(interceptor);
      rooms.remove(room);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void guestMergedListenerRacingMatchCompletionDoesNotLoseOrDoubleStatistics() throws Exception {
    UUID guest = account(true), user = account(false);
    var event =
        match(
            MatchType.SOLO,
            MatchEndStatus.FINISHED,
            false,
            List.of(player(guest, true, 1, false, 2)),
            null);
    var merge = new GuestMerged(UUID.randomUUID(), guest, user, Instant.now());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = CompletableFuture.runAsync(() -> writer.record(event), executor);
      var b =
          CompletableFuture.runAsync(
              () ->
                  new TransactionTemplate(transactions)
                      .executeWithoutResult(tx -> events.publishEvent(merge)),
              executor);
      CompletableFuture.allOf(a, b).get(10, TimeUnit.SECONDS);
    }
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(queries.stats(user).getXp()).isEqualTo(2000);
              assertThat(queries.history(user, 0, 10).getTotalItems()).isEqualTo(1);
            });
    writer.merge(merge);
    writer.record(event);
    assertThat(queries.stats(user).getMatchesPlayed()).isEqualTo(1);
    assertThat(queries.stats(guest).getXp()).isZero();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void profileIncludesAccountAndProgressionDataAndUpdatesOnlyTheCallersNickname() throws Exception {
    UUID user = account(false), other = account(false);
    writer.record(
        match(
            MatchType.SOLO,
            MatchEndStatus.FINISHED,
            false,
            List.of(player(user, false, 1, false, 1)),
            null));
    jdbc.update(
        "insert into auth_identity(id,user_id,provider,provider_subject) values (?,?,'GOOGLE',?)",
        UUID.randomUUID(),
        user,
        user.toString());
    jdbc.update(
        "update app_user set email=? where id=?", "profile-" + user + "@example.test", user);
    mvc.perform(get("/api/v1/me/profile").with(jwt().jwt(j -> j.subject(user.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(user.toString()))
        .andExpect(jsonPath("$.kind").value("REGISTERED"))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.xp").value(1000))
        .andExpect(jsonPath("$.rankPoints").value(0))
        .andExpect(jsonPath("$.linkedProviders[0]").value("GOOGLE"))
        .andExpect(jsonPath("$.createdAt").isString())
        .andExpect(jsonPath("$.email").value("profile-" + user + "@example.test"));
    String previousOther =
        jdbc.queryForObject("select nickname from app_user where id=?", String.class, other);
    mvc.perform(
            patch("/api/v1/me/profile")
                .with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"New Player\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("New Player"))
        .andExpect(jsonPath("$.xp").value(1000));
    assertThat(jdbc.queryForObject("select nickname from app_user where id=?", String.class, user))
        .isEqualTo("New Player");
    assertThat(jdbc.queryForObject("select nickname from app_user where id=?", String.class, other))
        .isEqualTo(previousOther);
  }

  @Test
  void profileRejectsAnonymousGuestEditsLockedAccountsAndInvalidNames() throws Exception {
    UUID guest = account(true), user = account(false), locked = account(false);
    jdbc.update("update app_user set status='LOCKED',lock_reason='Test lock' where id=?", locked);
    mvc.perform(get("/api/v1/me/profile")).andExpect(status().isUnauthorized());
    mvc.perform(
            patch("/api/v1/me/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"New Player\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me/profile").with(jwt().jwt(j -> j.subject(guest.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kind").value("GUEST"))
        .andExpect(jsonPath("$.linkedProviders").isEmpty());
    mvc.perform(
            patch("/api/v1/me/profile")
                .with(jwt().jwt(j -> j.subject(guest.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"New Player\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("GUEST_PROFILE_READ_ONLY"));
    mvc.perform(get("/api/v1/me/profile").with(jwt().jwt(j -> j.subject(locked.toString()))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    mvc.perform(
            patch("/api/v1/me/profile")
                .with(jwt().jwt(j -> j.subject(locked.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"New Player\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    String original =
        jdbc.queryForObject("select nickname from app_user where id=?", String.class, user);
    for (String nickname : List.of("ab", "x".repeat(21), "địt", "   ")) {
      mvc.perform(
              patch("/api/v1/me/profile")
                  .with(jwt().jwt(j -> j.subject(user.toString())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(java.util.Map.of("nickname", nickname))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
    assertThat(jdbc.queryForObject("select nickname from app_user where id=?", String.class, user))
        .isEqualTo(original);
  }

  private UUID account(boolean guest) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into app_user(id,kind,nickname) values (?,?,?)",
        id,
        guest ? "GUEST" : "REGISTERED",
        "Player" + id.toString().substring(0, 6));
    return id;
  }

  static MatchFinished match(
      MatchType type,
      MatchEndStatus status,
      boolean ranked,
      List<PlayerSummary> players,
      UUID room) {
    int rounds = players.stream().mapToInt(p -> p.results().size()).max().orElse(0);
    List<RoundSummary> summaries = new ArrayList<>();
    for (int i = 1; i <= rounds; i++) {
      summaries.add(
          new RoundSummary(
              i,
              UUID.randomUUID(),
              "Snapshot title " + i,
              "Snapshot artist",
              "DEEZER",
              "track" + i));
    }
    return new MatchFinished(
        UUID.randomUUID(),
        UUID.randomUUID(),
        type,
        type == MatchType.QUICK_MATCH ? "RANKED" : "PRACTICE",
        ranked,
        status,
        5,
        null,
        null,
        Instant.now().minusSeconds(300),
        Instant.now(),
        summaries,
        players,
        room);
  }

  static PlayerSummary player(UUID user, boolean guest, int rank, boolean left, int correct) {
    List<RoundPlayerResult> results = new ArrayList<>();
    for (int i = 1; i <= correct; i++) {
      results.add(new RoundPlayerResult(i, RoundOutcome.CORRECT, 1, 1000, 0, 0, 1000, 0, 100L));
    }
    return new PlayerSummary(
        user, "Player", guest, correct * 1000, rank, correct, correct * 100L, results, left);
  }
}
