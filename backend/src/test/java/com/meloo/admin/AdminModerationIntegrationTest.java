package com.meloo.admin;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.meloo.IntegrationTest;
import com.meloo.admin.analytics.AnalyticsListener;
import com.meloo.admin.audit.AuditTrail;
import com.meloo.admin.moderation.AdminModerationService;
import com.meloo.chat.api.ChatAdminApi;
import com.meloo.chat.api.ChatContextMessage;
import com.meloo.daily.events.DailyAttemptFinished;
import com.meloo.identity.api.UserAccounts;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Import(AdminModerationIntegrationTest.Time.class)
@TestPropertySource(
    properties = {
      "meloo.identity.jwt.secret=moderation-test-secret-with-at-least-32-bytes",
      "meloo.audio.hmac-secret=moderation-audio-secret-with-at-least-32-bytes",
      "meloo.admin.retention-cron=-"
    })
class AdminModerationIntegrationTest extends IntegrationTest {
  private static final Instant NOW = Instant.parse("2026-11-29T17:00:00Z");
  private static final String SECRET = "moderation-test-secret-with-at-least-32-bytes";

  @TestConfiguration(proxyBeanMethods = false)
  static class Time {
    @Bean
    @Primary
    Clock moderationClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired ChatAdminApi chat;
  @Autowired AdminModerationService moderation;
  @Autowired com.meloo.admin.daily.AdminDailyService daily;
  @Autowired com.meloo.progression.api.RankPointsQuery ranks;
  @Autowired AnalyticsListener listener;
  @Autowired ApplicationEventPublisher events;
  @Autowired PlatformTransactionManager transactions;
  @MockitoSpyBean AuditTrail audit;
  @MockitoSpyBean UserAccounts accounts;
  private UUID admin;
  private UUID user;

  @BeforeEach
  void seed() {
    admin = account("ADMIN");
    user = account("USER");
    jdbc.update(
        "insert into player_stats(user_id, is_guest, ranked_score) values (?, false, 145)", user);
  }

  @Test
  void allNewRoutesEnforceLiveAdminRole() throws Exception {
    for (String path :
        List.of("/users", "/users/" + user, "/reports", "/banned-words", "/daily-schedule")) {
      mvc.perform(get("/api/v1/admin" + path)).andExpect(status().isUnauthorized());
      mvc.perform(get("/api/v1/admin" + path).header("Authorization", token(user, "USER")))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/admin" + path).header("Authorization", token(user, "ADMIN")))
          .andExpect(status().isForbidden());
    }
    for (var request :
        List.of(
            put("/api/v1/admin/users/" + user + "/lock"),
            put("/api/v1/admin/users/" + user + "/role"),
            post("/api/v1/admin/reports/" + user + "/resolve"),
            post("/api/v1/admin/banned-words"),
            put("/api/v1/admin/banned-words/" + user),
            delete("/api/v1/admin/banned-words/" + user),
            put("/api/v1/admin/daily-schedule/2026-12-01"))) {
      mvc.perform(
              request
                  .header("Authorization", token(user, "USER"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get("/api/v1/admin/users").header("Authorization", token(user, "GUEST")))
        .andExpect(status().isForbidden());
    jdbc.update("update app_user set status='LOCKED', lock_reason='test' where id=?", admin);
    mvc.perform(get("/api/v1/admin/users").header("Authorization", token(admin, "ADMIN")))
        .andExpect(status().isForbidden());
  }

  @Test
  void lockRevokesLoggedInSessionAndRoleAndUnlockHaveAtomicAudit() throws Exception {
    String raw = UUID.randomUUID().toString();
    String hash =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    jdbc.update(
        "insert into refresh_token(id,user_id,family_id,token_hash,expires_at) values (?,?,?,?,?)",
        UUID.randomUUID(),
        user,
        UUID.randomUUID(),
        hash,
        Timestamp.from(NOW.plusSeconds(3600)));
    mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("meloo_refresh", raw)))
        .andExpect(status().isOk());
    perform(put("/api/v1/admin/users/" + user + "/lock"), "{\"locked\":true,\"reason\":\"Spam\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rankPoints").value(145))
        .andExpect(jsonPath("$.lockReason").value("Spam"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from refresh_token where user_id=? and revoked_at is null",
                Integer.class,
                user))
        .isZero();
    mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("meloo_refresh", raw)))
        .andExpect(status().isUnauthorized());
    perform(put("/api/v1/admin/users/" + user + "/lock"), "{\"locked\":false}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    perform(put("/api/v1/admin/users/" + user + "/role"), "{\"role\":\"ADMIN\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));
    var rows =
        jdbc.queryForList(
            "select action,actor_id,reason,detail::text from audit_log where target_id=? order by created_at,id",
            user.toString());
    assertThat(rows).hasSize(3).allSatisfy(row -> assertThat(row.get("actor_id")).isEqualTo(admin));
    var locked =
        rows.stream()
            .filter(row -> row.get("action").equals("USER_LOCKED"))
            .findFirst()
            .orElseThrow();
    assertThat(locked.get("reason")).isEqualTo("Spam");
    var detail = json.readTree((String) locked.get("detail"));
    assertThat(detail.at("/before/status").asString()).isEqualTo("ACTIVE");
    assertThat(detail.at("/after/status").asString()).isEqualTo("LOCKED");
    perform(put("/api/v1/admin/users/" + admin + "/lock"), "{\"locked\":true,\"reason\":\"test\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ADMIN_SELF_ACTION_FORBIDDEN"));
    perform(put("/api/v1/admin/users/" + admin + "/role"), "{\"role\":\"USER\"}")
        .andExpect(status().isConflict());
    perform(put("/api/v1/admin/users/" + user + "/lock"), "{\"locked\":true,\"reason\":\" \"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void searchAndDetailUseRealRankPointsAndUnsupportedFiltersAreExplicit() throws Exception {
    mvc.perform(
            get("/api/v1/admin/users")
                .param("q", "p" + user.toString().substring(0, 12))
                .header("Authorization", token(admin, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].rankPoints").value(145));
    mvc.perform(get("/api/v1/admin/users/" + user).header("Authorization", token(admin, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rankPoints").value(145));
    for (String filter : List.of("kind", "role", "status")) {
      String value =
          switch (filter) {
            case "kind" -> "REGISTERED";
            case "role" -> "USER";
            default -> "ACTIVE";
          };
      mvc.perform(
              get("/api/v1/admin/users")
                  .param(filter, value)
                  .header("Authorization", token(admin, "ADMIN")))
          .andExpect(status().isNotImplemented());
    }
  }

  @Test
  void reportsExposeContextResolveAndLockSenderTogether() throws Exception {
    var report =
        chat.createReport(
            "bad message",
            user,
            "Sender",
            admin,
            List.of(new ChatContextMessage(UUID.randomUUID(), admin, "Admin", "context", NOW)));
    mvc.perform(get("/api/v1/admin/reports").header("Authorization", token(admin, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].context[0].content").value("context"));
    perform(
            post("/api/v1/admin/reports/" + report.id() + "/resolve"),
            "{\"status\":\"ACTIONED\",\"lockSender\":true,\"lockReason\":\"Abuse\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.handledBy").value(admin.toString()));
    assertThat(jdbc.queryForObject("select status from app_user where id=?", String.class, user))
        .isEqualTo("LOCKED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_log where target_id in (?,?)",
                Integer.class,
                user.toString(),
                report.id().toString()))
        .isEqualTo(2);
    perform(
            post("/api/v1/admin/reports/" + report.id() + "/resolve"),
            "{\"status\":\"DISMISSED\",\"lockSender\":true,\"lockReason\":\"Abuse\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void failedAuditRollsBackAccountReportAndTokens() {
    var report = chat.createReport("bad", user, "Sender", admin, List.of());
    failAudit("CHAT_REPORT_RESOLVED");
    assertThatThrownBy(
            () ->
                moderation.resolve(
                    admin, report.id(), com.meloo.chat.api.ReportStatus.ACTIONED, true, "Abuse"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(jdbc.queryForObject("select status from app_user where id=?", String.class, user))
        .isEqualTo("ACTIVE");
    assertThat(chat.findReportById(report.id()).orElseThrow().status())
        .isEqualTo(com.meloo.chat.api.ReportStatus.OPEN);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_log where target_id=?", Integer.class, user.toString()))
        .isZero();
  }

  @Test
  void bannedWordCrudRefreshesMatcherAndAuditsSnapshots() throws Exception {
    String word = "word" + UUID.randomUUID().toString().replace("-", "");
    assertThat(chat.containsBannedWord(word)).isFalse();
    var created =
        perform(
                post("/api/v1/admin/banned-words"),
                json.writeValueAsString(Map.of("word", word.toUpperCase(Locale.ROOT))))
            .andExpect(status().isCreated())
            .andReturn();
    String id = json.readTree(created.getResponse().getContentAsString()).get("id").asString();
    assertThat(chat.containsBannedWord(word)).isTrue();
    perform(
            put("/api/v1/admin/banned-words/" + id),
            json.writeValueAsString(Map.of("word", word + "changed")))
        .andExpect(status().isOk());
    assertThat(chat.containsBannedWord(word)).isFalse();
    perform(delete("/api/v1/admin/banned-words/" + id), "").andExpect(status().isNoContent());
    assertThat(chat.containsBannedWord(word + "changed")).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_log where target_id=?", Integer.class, id))
        .isEqualTo(3);
  }

  @Test
  void schedulesOnlyFutureVietnamDatesAndAuditsReplacement() throws Exception {
    UUID track = track();
    String date = "2026-12-01";
    perform(
            put("/api/v1/admin/daily-schedule/" + date),
            json.writeValueAsString(Map.of("trackId", track)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Scheduled song"))
        .andExpect(jsonPath("$.locked").value(false));
    perform(
            put("/api/v1/admin/daily-schedule/" + date),
            json.writeValueAsString(Map.of("trackId", track)))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/admin/daily-schedule")
                .param("from", date)
                .param("to", date)
                .header("Authorization", token(admin, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].trackId").value(track.toString()));
    for (String locked : List.of("2026-11-29", "2026-11-30"))
      perform(
              put("/api/v1/admin/daily-schedule/" + locked),
              json.writeValueAsString(Map.of("trackId", track)))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("DAILY_CHALLENGE_LOCKED"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_log where actor_id=? and action='DAILY_SCHEDULED'",
                Integer.class,
                admin))
        .isEqualTo(2);
  }

  @Test
  void dailyEventAfterCommitIsIdempotentAndDoesNotStoreAnswers() {
    UUID eventId = UUID.randomUUID();
    var event =
        new DailyAttemptFinished(
            eventId,
            eventId,
            user,
            false,
            LocalDate.parse("2026-11-30"),
            "SOLVED",
            "CORRECT",
            2,
            800,
            5000,
            NOW);
    new TransactionTemplate(transactions).executeWithoutResult(s -> events.publishEvent(event));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(eventCount(eventId)).isEqualTo(1));
    AnalyticsListener target = AopTestUtils.getUltimateTargetObject(listener);
    target.on(event);
    assertThat(eventCount(eventId)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select props::text from analytics_event where props->>'eventId'=?",
                String.class,
                eventId.toString()))
        .contains("2026-11-30")
        .doesNotContain("score", "trackId", "preview", "token");
  }

  @Test
  void bulkRankQueryIncludesNewPlayersAtZeroWithoutInventingExistingPoints() {
    assertThat(ranks.findAll(List.of())).isEmpty();
    assertThat(ranks.findAll(List.of(admin, user, user)))
        .containsExactlyInAnyOrderEntriesOf(Map.of(admin, 0, user, 145));
  }

  @Test
  void failedAuditRollsBackBannedWordsAndDailySchedule() {
    String word = "rollback" + UUID.randomUUID().toString().replace("-", "");
    failAudit("BANNED_WORD_CREATED");
    assertThatThrownBy(() -> moderation.word(admin, null, word))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(chat.listBannedWords()).noneMatch(w -> w.word().equals(word));
    failAudit("DAILY_SCHEDULED");
    LocalDate date = LocalDate.parse("2026-12-02");
    assertThatThrownBy(() -> daily.schedule(admin, date, track()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from daily_challenge where challenge_date=?", Integer.class, date))
        .isZero();
  }

  @Test
  void moderationErrorsMatchContractAndDuplicateWordDoesNotWriteAudit() throws Exception {
    UUID missing = UUID.randomUUID();
    mvc.perform(
            get("/api/v1/admin/users/" + missing).header("Authorization", token(admin, "ADMIN")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    perform(delete("/api/v1/admin/banned-words/" + missing), "")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BANNED_WORD_NOT_FOUND"));
    String word = "duplicate" + missing.toString().replace("-", "");
    perform(post("/api/v1/admin/banned-words"), json.writeValueAsString(Map.of("word", word)))
        .andExpect(status().isCreated());
    perform(post("/api/v1/admin/banned-words"), json.writeValueAsString(Map.of("word", word)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BANNED_WORD_ALREADY_EXISTS"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_log where actor_id=?", Integer.class, admin))
        .isEqualTo(1);
  }

  @Test
  void guestCannotBePromotedAndRejectedChangeDoesNotWriteAudit() throws Exception {
    jdbc.update("update app_user set kind='GUEST' where id=?", user);
    perform(put("/api/v1/admin/users/" + user + "/role"), "{\"role\":\"ADMIN\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    assertThat(jdbc.queryForObject("select role from app_user where id=?", String.class, user))
        .isEqualTo("USER");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_log where target_id=?", Integer.class, user.toString()))
        .isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"lock", "role"})
  void concurrentProfileUpdateIsPreservedInMutationAndAudit(String action) throws Exception {
    String nickname = "Concurrent" + user.toString().substring(0, 6);
    var intercepted = new AtomicBoolean();
    try (var pool = Executors.newSingleThreadExecutor()) {
      doAnswer(
              call -> {
                Object snapshot = call.callRealMethod();
                if (intercepted.compareAndSet(false, true)) {
                  pool.submit(() -> accounts.updateNickname(user, nickname))
                      .get(20, TimeUnit.SECONDS);
                }
                return snapshot;
              })
          .when(accounts)
          .get(user);
      var result =
          action.equals("lock")
              ? moderation.lock(admin, user, true, "Reason")
              : moderation.role(admin, user, "ADMIN");
      assertThat(result.nickname()).isEqualTo(nickname);
      assertThat(
              jdbc.queryForObject("select nickname from app_user where id=?", String.class, user))
          .isEqualTo(nickname);
      var detail =
          json.readTree(
              jdbc.queryForObject(
                  "select detail::text from audit_log where target_id=?",
                  String.class,
                  user.toString()));
      assertThat(detail.at("/before/nickname").asString()).isEqualTo(nickname);
      assertThat(detail.at("/after/nickname").asString()).isEqualTo(nickname);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from audit_log where target_id=?",
                  Integer.class,
                  user.toString()))
          .isEqualTo(1);
    }
  }

  private UUID account(String role) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into app_user(id,kind,role,nickname) values (?,'REGISTERED',?,?)",
        id,
        role,
        "p" + id.toString().substring(0, 12));
    return id;
  }

  private UUID track() {
    UUID artist = UUID.randomUUID(), track = UUID.randomUUID();
    jdbc.update(
        "insert into artist(id,provider,provider_artist_id,name,name_normalized) values (?,'DEEZER',?,'Artist','artist')",
        artist,
        artist.toString());
    jdbc.update(
        "insert into track(id,provider,provider_track_id,title,title_normalized,primary_artist_id,enabled) values (?,'DEEZER',?,'Scheduled song','scheduled song',?,true)",
        track,
        track.toString(),
        artist);
    return track;
  }

  private long eventCount(UUID id) {
    return jdbc.queryForObject(
        "select count(*) from analytics_event where props->>'eventId'=?",
        Long.class,
        id.toString());
  }

  private void failAudit(String action) {
    doThrow(new org.springframework.dao.DataIntegrityViolationException("Audit unavailable"))
        .when(audit)
        .append(eq(admin), eq(action), anyString(), any(), any(), any(), any());
  }

  private org.springframework.test.web.servlet.ResultActions perform(
      MockHttpServletRequestBuilder request, String body) throws Exception {

    return mvc.perform(
        request
            .header("Authorization", token(admin, "ADMIN"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private static String token(UUID id, String role) throws Exception {
    var jwt =
        new SignedJWT(
            new JWSHeader(JWSAlgorithm.HS256),
            new JWTClaimsSet.Builder()
                .subject(id.toString())
                .claim("role", role)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build());
    jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
    return "Bearer " + jwt.serialize();
  }
}
