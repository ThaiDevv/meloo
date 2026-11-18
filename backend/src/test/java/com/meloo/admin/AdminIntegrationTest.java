package com.meloo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.meloo.IntegrationTest;
import com.meloo.admin.analytics.AnalyticsListener;
import com.meloo.admin.analytics.AnalyticsQueries;
import com.meloo.admin.analytics.AnalyticsRetentionJob;
import com.meloo.admin.analytics.AnalyticsWriter;
import com.meloo.admin.audit.AuditTrail;
import com.meloo.admin.catalog.AdminCatalogService;
import com.meloo.catalog.api.AdminImportResult;
import com.meloo.catalog.api.AdminTrackView;
import com.meloo.catalog.api.CatalogAdminApi;
import com.meloo.catalog.api.CatalogAdminException;
import com.meloo.game.events.*;
import com.meloo.identity.api.UserAccount;
import com.meloo.identity.api.UserAccounts;
import com.meloo.identity.events.GuestCreated;
import com.meloo.identity.events.GuestMerged;
import com.meloo.identity.events.UserRegistered;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Import(AdminIntegrationTest.FixedTime.class)
@TestPropertySource(
    properties = {
      "meloo.identity.jwt.secret=admin-test-secret-with-at-least-32-bytes",
      "meloo.audio.hmac-secret=admin-audio-test-secret-with-at-least-32-bytes",
      "meloo.admin.beta-start=2026-11-01",
      "meloo.admin.excluded-user-ids[0]=00000000-0000-0000-0000-000000000099",
      "meloo.admin.retention-cron=-"
    })
class AdminIntegrationTest extends IntegrationTest {
  private static final Instant NOW = Instant.parse("2026-11-29T05:00:00Z");
  private static final UUID ADMIN = new UUID(0, 1);
  private static final UUID USER = new UUID(0, 2);
  private static final UUID EXCLUDED = UUID.fromString("00000000-0000-0000-0000-000000000099");
  private static final String SECRET = "admin-test-secret-with-at-least-32-bytes";

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedTime {
    @Bean
    @Primary
    Clock adminTestClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired AnalyticsWriter writer;
  @Autowired AnalyticsListener listener;
  @Autowired AnalyticsQueries queries;
  @Autowired AnalyticsRetentionJob retention;
  @MockitoSpyBean AuditTrail audit;
  @Autowired AdminCatalogService writes;
  @MockitoSpyBean CatalogAdminApi catalog;
  @Autowired PlatformTransactionManager transactions;
  @Autowired ApplicationEventPublisher events;
  @Autowired ObjectMapper json;
  @MockitoBean UserAccounts users;

  @BeforeEach
  void prepare() {
    jdbc.update("delete from analytics_event");
    when(users.get(ADMIN)).thenReturn(account(ADMIN, "ADMIN", "ACTIVE"));
    when(users.get(USER)).thenReturn(account(USER, "USER", "ACTIVE"));
    when(users.search(anyString(), anyInt(), anyInt()))
        .thenReturn(
            new UserAccounts.UserPage(List.of(account(ADMIN, "ADMIN", "ACTIVE")), 1, 0, 100));
  }

  @Test
  void everyAdminRouteRejectsAnonymousUserAndGuest() throws Exception {
    String[] paths = {"/dashboard", "/analytics", "/tracks", "/playlists", "/audit-logs"};
    for (String path : paths) {
      mvc.perform(get("/api/v1/admin" + path)).andExpect(status().isUnauthorized());
      mvc.perform(get("/api/v1/admin" + path).header("Authorization", token(USER, "USER")))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
    for (var request :
        List.of(
            put("/api/v1/admin/tracks/" + USER + "/status"),
            put("/api/v1/admin/tracks/" + USER + "/metadata"),
            post("/api/v1/admin/tracks/import"),
            post("/api/v1/admin/playlists"),
            put("/api/v1/admin/playlists/" + USER),
            delete("/api/v1/admin/playlists/" + USER),
            post("/api/v1/admin/playlists/" + USER + "/tracks"),
            delete("/api/v1/admin/playlists/" + USER + "/tracks"))) {
      mvc.perform(
              request
                  .header("Authorization", token(USER, "USER"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", token(USER, "GUEST")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", token(USER, "ADMIN")))
        .andExpect(status().isForbidden());
    when(users.get(ADMIN)).thenReturn(account(ADMIN, "ADMIN", "LOCKED"));
    mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", token(ADMIN, "ADMIN")))
        .andExpect(status().isForbidden());
  }

  @Test
  void committedEventIsIngestedAndConcurrentRedeliveryIsIdempotent() throws Exception {
    UUID id = UUID.randomUUID();
    var event = new GuestCreated(id, USER, "Player", NOW);
    new TransactionTemplate(transactions).executeWithoutResult(s -> events.publishEvent(event));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(eventCount(id)).isEqualTo(1));
    try (var pool = Executors.newFixedThreadPool(8)) {
      var barrier = new CyclicBarrier(8);
      var results = new ArrayList<Future<?>>();
      for (int i = 0; i < 8; i++) {
        results.add(
            pool.submit(
                () -> {
                  barrier.await(10, TimeUnit.SECONDS);
                  writer.record(id, "GUEST_CREATED", USER, NOW, Map.of());
                  return null;
                }));
      }
      for (var result : results) result.get(20, TimeUnit.SECONDS);
    }
    assertThat(eventCount(id)).isEqualTo(1);
  }

  @Test
  void allListenersStoreOnlyAnalyticsAndReplaySafely() {
    AnalyticsListener target = AopTestUtils.getUltimateTargetObject(listener);
    UUID guest = UUID.randomUUID();
    var created = new GuestCreated(UUID.randomUUID(), guest, "PrivateNickname", NOW);
    var registered = new UserRegistered(UUID.randomUUID(), USER, NOW);
    var merge = new GuestMerged(UUID.randomUUID(), guest, USER, NOW);
    var start =
        new MatchStarted(
            UUID.randomUUID(), UUID.randomUUID(), MatchType.SOLO, false, 1, NOW, List.of(USER));
    var finish = match(USER, NOW, null, MatchEndStatus.FINISHED, false);
    for (int i = 0; i < 2; i++) {
      target.on(created);
      target.on(registered);
      target.on(merge);
      target.on(start);
      target.on(finish);
    }
    assertThat(jdbc.queryForObject("select count(*) from analytics_event", Integer.class))
        .isEqualTo(5);
    assertThat(jdbc.queryForList("select props::text from analytics_event", String.class))
        .allSatisfy(
            props ->
                assertThat(props)
                    .doesNotContain(
                        "PrivateNickname", "nickname", "rounds", "score", "token", "preview"));
  }

  @Test
  void sampleKpisRespectVietnamDaysMergesCutoffAndExcludedPlayers() {
    AnalyticsListener target = AopTestUtils.getUltimateTargetObject(listener);
    UUID guest = UUID.randomUUID();
    UUID returning = UUID.randomUUID();
    UUID once = UUID.randomUUID();
    UUID late = UUID.randomUUID();
    UUID preBeta = UUID.randomUUID();
    UUID playlist = UUID.randomUUID();
    target.on(new GuestCreated(UUID.randomUUID(), guest, "Guest", at("2026-11-01")));
    target.on(new GuestCreated(UUID.randomUUID(), once, "Once", at("2026-11-01")));
    // Merge arrives before the older guest match; identities must still collapse.
    target.on(new GuestMerged(UUID.randomUUID(), guest, returning, at("2026-11-05")));
    target.on(match(guest, at("2026-11-02"), playlist, MatchEndStatus.FINISHED, false));
    target.on(match(returning, at("2026-11-29"), playlist, MatchEndStatus.FINISHED, false));
    target.on(match(once, at("2026-11-27"), playlist, MatchEndStatus.FINISHED, false));
    target.on(
        match(once, at("2026-11-27").plusSeconds(300), playlist, MatchEndStatus.FINISHED, false));
    target.on(match(late, at("2026-11-28"), null, MatchEndStatus.FINISHED, false));
    target.on(match(late, at("2026-11-29"), null, MatchEndStatus.FINISHED, false));
    target.on(match(preBeta, at("2026-10-31"), null, MatchEndStatus.FINISHED, false));
    target.on(match(preBeta, at("2026-11-29"), null, MatchEndStatus.FINISHED, false));
    for (UUID excluded : List.of(ADMIN, EXCLUDED)) {
      target.on(match(excluded, at("2026-11-03"), null, MatchEndStatus.FINISHED, false));
      target.on(match(excluded, at("2026-11-29"), null, MatchEndStatus.FINISHED, false));
    }
    target.on(match(UUID.randomUUID(), at("2026-11-04"), null, MatchEndStatus.ABANDONED, false));
    target.on(match(UUID.randomUUID(), at("2026-11-04"), null, MatchEndStatus.FINISHED, true));
    var result = queries.snapshot();
    // Eligible: merged returning player + once. Late first play and pre-beta player are ineligible.
    assertThat(result.returningPlayers().denominator()).isEqualTo(2);
    assertThat(result.returningPlayers().numerator()).isEqualTo(1);
    assertThat(result.returningPlayers().rate()).isEqualByComparingTo("0.5");
    assertThat(result.guestConversion().rate()).isEqualByComparingTo("0.5");
    assertThat(result.dau()).isEqualTo(3);
    assertThat(result.wau()).isEqualTo(4);
    assertThat(result.popularPlaylists())
        .containsExactly(new AnalyticsQueries.PopularPlaylist(playlist, 4));
    assertThat(result.matchesPerDay())
        .contains(new AnalyticsQueries.MatchesPerDay(LocalDate.parse("2026-11-27"), 2));
    assertThat(queries.matchesToday()).isEqualTo(5);
  }

  @Test
  void analyticsEndpointReturnsZeroFilledDailySeriesWithReturningPlayers() throws Exception {
    AnalyticsListener target = AopTestUtils.getUltimateTargetObject(listener);
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    target.on(match(first, at("2026-11-24"), null, MatchEndStatus.FINISHED, false));
    target.on(match(first, at("2026-11-29"), null, MatchEndStatus.FINISHED, false));
    target.on(match(second, at("2026-11-29"), null, MatchEndStatus.FINISHED, false));
    target.on(match(ADMIN, at("2026-11-29"), null, MatchEndStatus.FINISHED, false));
    mvc.perform(
            get("/api/v1/admin/analytics")
                .param("days", "7")
                .header("Authorization", token(ADMIN, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2026-11-23"))
        .andExpect(jsonPath("$.to").value("2026-11-29"))
        .andExpect(jsonPath("$.daily.length()").value(7))
        .andExpect(jsonPath("$.daily[0].day").value("2026-11-23"))
        .andExpect(jsonPath("$.daily[0].activePlayers").value(0))
        .andExpect(jsonPath("$.daily[1].activePlayers").value(1))
        .andExpect(jsonPath("$.daily[1].returningPlayers").value(0))
        .andExpect(jsonPath("$.daily[5].matches").value(0))
        .andExpect(jsonPath("$.daily[6].activePlayers").value(2))
        .andExpect(jsonPath("$.daily[6].returningPlayers").value(1))
        .andExpect(jsonPath("$.daily[6].matches").value(3))
        .andExpect(jsonPath("$.dau").value(2))
        .andExpect(jsonPath("$.wau").value(2))
        // Second player first played after the beta cutoff (27/11), so only the first is eligible.
        .andExpect(jsonPath("$.returningRate.denominator").value(1))
        .andExpect(jsonPath("$.returningRate.numerator").value(1))
        .andExpect(jsonPath("$.guestConversion.rate").value(nullValue()));
    mvc.perform(
            get("/api/v1/admin/analytics")
                .param("days", "6")
                .header("Authorization", token(ADMIN, "ADMIN")))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/admin/analytics").header("Authorization", token(ADMIN, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.daily.length()").value(30));
  }

  @Test
  void midnightInVietnamStartsANewActivityDay() {
    AnalyticsListener target = AopTestUtils.getUltimateTargetObject(listener);
    target.on(
        match(USER, Instant.parse("2026-11-28T16:59:59Z"), null, MatchEndStatus.FINISHED, false));
    target.on(
        match(USER, Instant.parse("2026-11-28T17:00:00Z"), null, MatchEndStatus.FINISHED, false));
    var result = queries.snapshot();
    assertThat(result.matchesPerDay())
        .extracting(AnalyticsQueries.MatchesPerDay::day)
        .containsExactly(LocalDate.parse("2026-11-28"), LocalDate.parse("2026-11-29"));
    assertThat(queries.matchesToday()).isEqualTo(1);
    assertThat(result.returningPlayers().denominator()).isZero();
    assertThat(result.returningPlayers().rate()).isNull();
  }

  @Test
  void excludedGuestExcludesTheMergedPersonEvenWhenOnlyRegisteredIdPlayed() {
    AnalyticsListener target = AopTestUtils.getUltimateTargetObject(listener);
    UUID registered = UUID.randomUUID();
    target.on(new GuestMerged(UUID.randomUUID(), EXCLUDED, registered, at("2026-11-02")));
    target.on(match(registered, at("2026-11-03"), null, MatchEndStatus.FINISHED, false));
    target.on(match(registered, at("2026-11-29"), null, MatchEndStatus.FINISHED, false));
    var stats = queries.snapshot();
    assertThat(stats.dau()).isZero();
    assertThat(stats.returningPlayers().denominator()).isZero();
  }

  @Test
  void requiredNullableResponseFieldsArePresent() throws Exception {
    var tracks = seedTracks(1);
    var response =
        mvc.perform(
                get("/api/v1/admin/tracks")
                    .param("q", tracks.prefix)
                    .header("Authorization", token(ADMIN, "ADMIN")))
            .andExpect(status().isOk())
            .andReturn();
    var track = json.readTree(response.getResponse().getContentAsString()).at("/items/0");
    for (String field :
        List.of(
            "genreId", "genreCode", "countryCode", "releaseYear", "disabledReason", "coverUrl")) {
      assertThat(track.has(field)).as(field).isTrue();
      assertThat(track.get(field).isNull()).as(field).isTrue();
    }
    audit.append(ADMIN, "NULL_SNAPSHOT_TEST", "TEST", tracks.prefix, null, null, null);
    var audits =
        mvc.perform(
                get("/api/v1/admin/audit-logs")
                    .param("action", "NULL_SNAPSHOT_TEST")
                    .header("Authorization", token(ADMIN, "ADMIN")))
            .andExpect(status().isOk())
            .andReturn();
    var entry = json.readTree(audits.getResponse().getContentAsString()).at("/items/0");
    assertThat(entry.has("reason")).isTrue();
    assertThat(entry.at("/detail/before").isNull()).isTrue();
    assertThat(entry.at("/detail/after").isNull()).isTrue();
  }

  @Test
  void retentionDeletesStrictlyOlderThanConfigured90DaysAndRejectsExpiredReplay() {
    Instant cutoff = NOW.minus(90, ChronoUnit.DAYS);
    for (var time : List.of(cutoff.minusSeconds(1), cutoff, cutoff.plusSeconds(1))) {
      jdbc.update(
          "insert into analytics_event(event_type, occurred_at) values ('TEST', ?)",
          Timestamp.from(time));
    }
    assertThat(retention.purge()).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from analytics_event", Integer.class))
        .isEqualTo(2);
    writer.record(UUID.randomUUID(), "GUEST_CREATED", USER, cutoff.minusSeconds(1), Map.of());
    assertThat(jdbc.queryForObject("select count(*) from analytics_event", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void dashboardUsesIdentityCountsIncludingAccountsCreatedBeforeListener() throws Exception {
    when(users.countCreated(eq("REGISTERED"), any(), any())).thenReturn(37L);
    when(users.countCreated(eq("GUEST"), any(), any())).thenReturn(4L);
    AnalyticsListener target = AopTestUtils.getUltimateTargetObject(listener);
    target.on(match(USER, NOW, null, MatchEndStatus.FINISHED, false));
    mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", token(ADMIN, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRegisteredUsers").value(37))
        .andExpect(jsonPath("$.totalGuestsToday").value(4))
        .andExpect(jsonPath("$.matchesToday").value(1))
        .andExpect(jsonPath("$.dailyPlayersToday").value(0))
        .andExpect(jsonPath("$.activeRooms").value(0))
        .andExpect(jsonPath("$.onlinePlayers").value(0))
        .andExpect(jsonPath("$.serverTime").value(NOW.toString()))
        .andExpect(jsonPath("$.dau").doesNotExist());
    verify(users).countCreated("REGISTERED", Instant.EPOCH, NOW.plusNanos(1));
  }

  @Test
  void catalogLifecycleWritesAtomicAuditWithBeforeAndAfterAndReturnsContractErrors()
      throws Exception {
    var tracks = seedTracks(6);
    String auth = token(ADMIN, "ADMIN");
    mvc.perform(get("/api/v1/admin/tracks").param("q", tracks.prefix).header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(6));
    UUID first = tracks.ids.getFirst();
    mvc.perform(
            put("/api/v1/admin/tracks/" + first + "/metadata")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"countryCode\":\"VN\",\"releaseYear\":2020}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.countryCode").value("VN"));
    mvc.perform(
            put("/api/v1/admin/tracks/" + first + "/status")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"disabledReason\":\"Broken preview\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
    var detail =
        jdbc.queryForObject(
            "select detail::text from audit_log where target_id = ? and action = 'TRACK_DISABLED'",
            String.class,
            first.toString());
    var tree = json.readTree(detail);
    assertThat(tree.at("/before/enabled").asBoolean()).isTrue();
    assertThat(tree.at("/after/enabled").asBoolean()).isFalse();
    assertThat(tree.at("/after/disabledReason").asString()).isEqualTo("Broken preview");
    mvc.perform(
            put("/api/v1/admin/tracks/" + first + "/status")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
        .andExpect(status().isOk());
    var request =
        Map.of(
            "code",
            tracks.prefix,
            "nameVi",
            "Nhạc",
            "nameEn",
            "Music",
            "trackIds",
            tracks.ids.subList(0, 5));
    var created =
        mvc.perform(
                post("/api/v1/admin/playlists")
                    .header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.enabledTrackCount").value(5))
            .andReturn();
    UUID playlist =
        UUID.fromString(
            json.readTree(created.getResponse().getContentAsString()).get("id").asString());
    mvc.perform(
            post("/api/v1/admin/playlists")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    mvc.perform(
            post("/api/v1/admin/playlists/" + playlist + "/tracks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("trackIds", List.of(tracks.ids.get(5))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabledTrackCount").value(6));
    mvc.perform(
            delete("/api/v1/admin/playlists/" + playlist + "/tracks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(Map.of("trackIds", List.of(first, tracks.ids.get(1))))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("NOT_ENOUGH_TRACKS"))
        .andExpect(jsonPath("$.missingTracks").value(1));
    assertThat(
            catalog.listPlaylists().stream()
                .filter(p -> p.id().equals(playlist))
                .findFirst()
                .orElseThrow()
                .enabledTrackCount())
        .isEqualTo(6);
    mvc.perform(
            delete("/api/v1/admin/playlists/" + playlist + "/tracks")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("trackIds", List.of(first)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabledTrackCount").value(5));
    mvc.perform(
            put("/api/v1/admin/playlists/" + playlist)
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nameVi\":\"Đổi tên\",\"nameEn\":\"Renamed\",\"enabled\":true}"))
        .andExpect(status().isOk());
    mvc.perform(delete("/api/v1/admin/playlists/" + playlist).header("Authorization", auth))
        .andExpect(status().isNoContent());
    mvc.perform(
            get("/api/v1/admin/audit-logs")
                .header("Authorization", auth)
                .param("targetType", "PLAYLIST")
                .param("actorId", ADMIN.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].detail.before").exists())
        .andExpect(jsonPath("$.items[0].createdAt").exists());
    // Make a standard playlist to assert the protected error, without depending on a seed job.
    UUID standard = UUID.randomUUID();
    jdbc.update(
        "insert into playlist(id, code, name_vi, name_en, kind, enabled) values (?, ?, 'Chuẩn', 'Standard', 'STANDARD', false)",
        standard,
        tracks.prefix + "-std");
    mvc.perform(delete("/api/v1/admin/playlists/" + standard).header("Authorization", auth))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STANDARD_PLAYLIST_PROTECTED"));
  }

  @Test
  void failedAuditRollsBackCatalogMutationAndAuditIsAppendOnly() {
    var tracks = seedTracks(1);
    doThrow(new org.springframework.dao.DataIntegrityViolationException("Audit unavailable"))
        .when(audit)
        .append(
            eq(ADMIN),
            eq("TRACK_DISABLED"),
            eq("TRACK"),
            eq(tracks.ids.getFirst().toString()),
            any(),
            any(),
            any());
    assertThatThrownBy(() -> writes.status(ADMIN, tracks.ids.getFirst(), false, "temporary"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            catalog
                .searchTracks(
                    new com.meloo.catalog.api.AdminTrackQuery(
                        tracks.prefix, true, null, null, null, 0, 10))
                .total())
        .isEqualTo(1);
    audit.append(ADMIN, "TEST_APPEND", "TEST", tracks.prefix, null, null, Map.of("ok", true));
    assertThatThrownBy(
            () -> jdbc.update("delete from audit_log where target_id = ?", tracks.prefix))
        .hasMessageContaining("append-only");
  }

  @Test
  void importCallsProviderOutsideTransactionAndPersistsIntentAndOutcome() throws Exception {
    String query = "import-" + UUID.randomUUID();
    var track =
        new AdminTrackView(
            UUID.randomUUID(),
            "Imported track",
            "Artist",
            "DEEZER",
            "123",
            null,
            null,
            null,
            null,
            true,
            null,
            null);
    doAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(
                      jdbc.queryForObject(
                          "select count(*) from audit_log where action = 'TRACK_IMPORT_REQUESTED' and detail -> 'after' ->> 'query' = ?",
                          Long.class,
                          query))
                  .isEqualTo(1);
              return new AdminImportResult(3, 1, 1, 1, List.of(track));
            })
        .when(catalog)
        .importByQuery(eq(query), eq(25), isNull(), isNull());
    mvc.perform(
            post("/api/v1/admin/tracks/import")
                .header("Authorization", token(ADMIN, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("query", query))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.importedCount").value(1))
        .andExpect(jsonPath("$.skippedNoPreviewCount").value(1))
        .andExpect(jsonPath("$.duplicateCount").value(1));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_log where action = 'TRACK_IMPORTED' and detail -> 'before' ->> 'query' = ? and detail -> 'after' ->> 'importedCount' = '1'",
                Long.class,
                query))
        .isEqualTo(1);
    String providerId = "missing-" + UUID.randomUUID();
    doThrow(
            new CatalogAdminException(
                CatalogAdminException.Reason.NOT_FOUND, "Provider track not found"))
        .when(catalog)
        .importByProviderTrackId(eq(providerId), isNull(), isNull());
    mvc.perform(
            post("/api/v1/admin/tracks/import")
                .header("Authorization", token(ADMIN, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("providerTrackId", providerId))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_log where action = 'TRACK_IMPORT_FAILED' and detail -> 'before' ->> 'providerTrackId' = ?",
                Long.class,
                providerId))
        .isEqualTo(1);
    mvc.perform(
            post("/api/v1/admin/tracks/import")
                .header("Authorization", token(ADMIN, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"a\",\"providerTrackId\":\"b\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/admin/tracks/import")
                .header("Authorization", token(ADMIN, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"a\",\"limit\":null}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/admin/audit-logs")
                .header("Authorization", token(ADMIN, "ADMIN"))
                .param("from", "2026-11-30T00:00:00Z")
                .param("to", "2026-11-01T00:00:00Z"))
        .andExpect(status().isBadRequest());
  }

  private Seed seedTracks(int count) {
    String prefix = "admin" + UUID.randomUUID().toString().substring(0, 8);
    UUID artist = UUID.randomUUID();
    jdbc.update(
        "insert into artist(id, provider, provider_artist_id, name, name_normalized) values (?, 'DEEZER', ?, ?, ?)",
        artist,
        prefix,
        prefix,
        prefix);
    var ids = new ArrayList<UUID>();
    for (int i = 0; i < count; i++) {
      UUID id = UUID.randomUUID();
      ids.add(id);
      jdbc.update(
          """
          insert into track(id, provider, provider_track_id, title, title_normalized, primary_artist_id, enabled)
          values (?, 'DEEZER', ?, ?, ?, ?, true)
          """,
          id,
          prefix + i,
          prefix + i,
          prefix + i,
          artist);
    }
    return new Seed(prefix, ids);
  }

  private record Seed(String prefix, List<UUID> ids) {}

  private long eventCount(UUID id) {
    return jdbc.queryForObject(
        "select count(*) from analytics_event where props ->> 'eventId' = ?",
        Long.class,
        id.toString());
  }

  private static Instant at(String date) {
    return LocalDate.parse(date).atStartOfDay(AnalyticsQueries.VIETNAM).toInstant();
  }

  private static MatchFinished match(
      UUID user, Instant ended, UUID playlist, MatchEndStatus status, boolean left) {
    return new MatchFinished(
        UUID.randomUUID(),
        UUID.randomUUID(),
        MatchType.SOLO,
        "SOLO",
        false,
        status,
        1,
        playlist,
        null,
        ended.minusSeconds(30),
        ended,
        List.of(),
        List.of(
            new PlayerSummary(user, "Hidden nickname", false, 100, 1, 1, 1000, List.of(), left)));
  }

  private static UserAccount account(UUID id, String role, String status) {
    return new UserAccount(
        id,
        "REGISTERED",
        role,
        status,
        "Player",
        null,
        null,
        null,
        NOW,
        List.of(),
        NOW,
        null,
        true);
  }

  private static String token(UUID id, String role) throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .subject(id.toString())
            .claim("role", role)
            .issueTime(new Date())
            .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
            .build();
    var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
    return "Bearer " + jwt.serialize();
  }
}
