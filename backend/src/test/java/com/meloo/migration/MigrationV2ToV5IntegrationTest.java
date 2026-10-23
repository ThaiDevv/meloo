package com.meloo.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meloo.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration test for migrations V2–V5. Verifies: migrations run on empty DB (via Flyway +
 * Testcontainers), constraints reject invalid data, and expected indexes exist.
 */
class MigrationV2ToV5IntegrationTest extends IntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  // --- helpers ---

  private List<String> indexesOn(String table) {
    return jdbc.queryForList(
        "select indexname from pg_indexes where tablename = ?", String.class, table);
  }

  private boolean extensionExists(String name) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from pg_extension where extname = ?)", Boolean.class, name));
  }

  // --- V2 identity ---

  @Nested
  @DisplayName("V2 — identity")
  class IdentityTests {

    @Test
    @DisplayName("app_user rejects invalid kind")
    void appUserRejectsInvalidKind() {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      insert into app_user (id, kind, role, status, nickname, last_active_at, created_at)
                      values (?, 'INVALID', 'USER', 'ACTIVE', 'test', now(), now())
                      """,
                      UUID.randomUUID()))
          .hasMessageContaining("app_user_kind_check");
    }

    @Test
    @DisplayName("app_user rejects LOCKED without lock_reason")
    void appUserRejectsLockedWithoutReason() {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      insert into app_user (id, kind, role, status, nickname, last_active_at, created_at)
                      values (?, 'GUEST', 'USER', 'LOCKED', 'test', now(), now())
                      """,
                      UUID.randomUUID()))
          .hasMessageContaining("chk_app_user_lock");
    }

    @Test
    @DisplayName("app_user rejects nickname shorter than 3 chars")
    void appUserRejectsShortNickname() {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      insert into app_user (id, kind, role, status, nickname, last_active_at, created_at)
                      values (?, 'GUEST', 'USER', 'ACTIVE', 'ab', now(), now())
                      """,
                      UUID.randomUUID()))
          .hasMessageContaining("app_user_nickname_check");
    }

    @Test
    @DisplayName("app_user rejects uppercase email")
    void appUserRejectsUppercaseEmail() {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      insert into app_user (id, kind, role, status, nickname, email, last_active_at, created_at)
                      values (?, 'REGISTERED', 'USER', 'ACTIVE', 'test', 'User@Example.com', now(), now())
                      """,
                      UUID.randomUUID()))
          .hasMessageContaining("chk_app_user_email_lower");
    }

    @Test
    @DisplayName("auth_identity rejects non-existent user_id")
    void authIdentityRejectsNonExistentUser() {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      insert into auth_identity (id, user_id, provider, provider_subject, created_at)
                      values (?, ?, 'GOOGLE', 'sub-123', now())
                      """,
                      UUID.randomUUID(),
                      UUID.randomUUID()))
          .hasMessageContaining("auth_identity_user_id_fkey");
    }

    @Test
    @DisplayName("identity indexes exist")
    void identityIndexesExist() {
      assertThat(indexesOn("app_user"))
          .contains("idx_app_user_kind_last_active", "idx_app_user_merged_into");
      assertThat(indexesOn("auth_identity")).contains("idx_auth_identity_user");
      assertThat(indexesOn("refresh_token"))
          .contains("idx_refresh_token_user", "idx_refresh_token_family");
    }
  }

  // --- V3 catalog ---

  @Nested
  @DisplayName("V3 — catalog")
  class CatalogTests {

    @Test
    @DisplayName("pg_trgm and unaccent extensions exist")
    void extensionsExist() {
      assertThat(extensionExists("pg_trgm")).isTrue();
      assertThat(extensionExists("unaccent")).isTrue();
    }

    @Test
    @DisplayName("playlist rejects invalid kind")
    void playlistRejectsInvalidKind() {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      insert into playlist (id, code, name_vi, name_en, kind, enabled, created_at)
                      values (?, 'test', 'Test', 'Test', 'INVALID', true, now())
                      """,
                      UUID.randomUUID()))
          .hasMessageContaining("playlist_kind_check");
    }

    @Test
    @DisplayName("catalog indexes exist, including GIN trigram")
    void catalogIndexesExist() {
      assertThat(indexesOn("artist")).contains("idx_artist_name_trgm");
      assertThat(indexesOn("track")).contains("idx_track_title_trgm", "idx_track_artist");
      assertThat(indexesOn("playlist_track")).contains("idx_playlist_track_track");

      String indexDef =
          jdbc.queryForObject(
              "select indexdef from pg_indexes where tablename = 'artist' and indexname = 'idx_artist_name_trgm'",
              String.class);
      assertThat(indexDef).containsIgnoringCase("USING gin").contains("gin_trgm_ops");
    }
  }

  // --- V4 progression ---

  @Nested
  @DisplayName("V4 — progression match history")
  class ProgressionTests {

    @Test
    @DisplayName("match rejects invalid type")
    void matchRejectsInvalidType() {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      insert into match (id, type, ranked, status, round_count, started_at, ended_at, created_at)
                      values (?, 'INVALID', false, 'FINISHED', 10, now(), now(), now())
                      """,
                      UUID.randomUUID()))
          .hasMessageContaining("match_type_check");
    }

    @Test
    @DisplayName("match rejects ABANDONED with ranked=true")
    void matchRejectsAbandonedRanked() {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      insert into match (id, type, ranked, status, round_count, started_at, ended_at, created_at)
                      values (?, 'SOLO', true, 'ABANDONED', 10, now(), now(), now())
                      """,
                      UUID.randomUUID()))
          .hasMessageContaining("chk_match_abandoned_not_ranked");
    }

    @Test
    @DisplayName("match rejects invalid round_count")
    void matchRejectsInvalidRoundCount() {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      insert into match (id, type, ranked, status, round_count, started_at, ended_at, created_at)
                      values (?, 'SOLO', false, 'FINISHED', 7, now(), now(), now())
                      """,
                      UUID.randomUUID()))
          .hasMessageContaining("match_round_count_check");
    }

    @Test
    @DisplayName("round_result rejects invalid outcome")
    void roundResultRejectsInvalidOutcome() {
      // Insert parent rows first
      UUID matchId = UUID.randomUUID();
      UUID playerId = UUID.randomUUID();
      UUID roundId = UUID.randomUUID();
      jdbc.update(
          """
          insert into match (id, type, ranked, status, round_count, started_at, ended_at, created_at)
          values (?, 'SOLO', false, 'FINISHED', 5, now(), now(), now())
          """,
          matchId);
      jdbc.update(
          """
          insert into match_player (id, match_id, nickname, is_guest, total_score, rank,
            correct_count, correct_time_ms, counts_for_leaderboard, ended_at, created_at)
          values (?, ?, 'tester', false, 0, 1, 0, 0, false, now(), now())
          """,
          playerId,
          matchId);
      jdbc.update(
          """
          insert into round (id, match_id, round_no, track_id, track_title, artist_name, created_at)
          values (?, ?, 1, ?, 'Song', 'Artist', now())
          """,
          roundId,
          matchId,
          UUID.randomUUID());

      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      insert into round_result (id, round_id, match_player_id, outcome,
                        base_score, combo_percent, speed_bonus, round_score, wrong_count, created_at)
                      values (?, ?, ?, 'INVALID', 0, 0, 0, 0, 0, now())
                      """,
                      UUID.randomUUID(),
                      roundId,
                      playerId))
          .hasMessageContaining("round_result_outcome_check");
    }

    @Test
    @DisplayName("progression indexes exist, including partial leaderboard index")
    void progressionIndexesExist() {
      assertThat(indexesOn("match")).contains("idx_match_ended_at", "idx_match_type_ended");
      assertThat(indexesOn("match_player"))
          .contains("idx_match_player_user_matches", "idx_match_player_leaderboard");
      assertThat(indexesOn("round_result")).contains("idx_round_result_player");

      String indexDef =
          jdbc.queryForObject(
              "select indexdef from pg_indexes where tablename = 'match_player' and indexname = 'idx_match_player_leaderboard'",
              String.class);
      assertThat(indexDef).containsIgnoringCase("WHERE counts_for_leaderboard");
    }
  }

  // --- V5 chat ---

  @Nested
  @DisplayName("V5 — chat")
  class ChatTests {

    @Test
    @DisplayName("banned_word rejects uppercase word")
    void bannedWordRejectsUppercase() {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "insert into banned_word (id, word, created_at) values (?, 'ABC', now())",
                      UUID.randomUUID()))
          .hasMessageContaining("banned_word_word_check");
    }

    @Test
    @DisplayName("banned_word seed has exactly 25 rows")
    void bannedWordSeedCount() {
      Integer count = jdbc.queryForObject("select count(*) from banned_word", Integer.class);
      assertThat(count).isEqualTo(25);
    }

    @Test
    @DisplayName("banned_word seed contains single-token entries only")
    void bannedWordSeedHasNoPhrases() {
      Integer phrases =
          jdbc.queryForObject("select count(*) from banned_word where word ~ '\\s'", Integer.class);
      assertThat(phrases).isZero();
    }

    @Test
    @DisplayName("chat_report rejects invalid status")
    void chatReportRejectsInvalidStatus() {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      insert into chat_report (id, message_text, sender_id, sender_nickname,
                        reporter_id, status, created_at)
                      values (?, 'msg', ?, 'nick', ?, 'INVALID', now())
                      """,
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      UUID.randomUUID()))
          .hasMessageContaining("chat_report_status_check");
    }

    @Test
    @DisplayName("chat indexes exist")
    void chatIndexesExist() {
      assertThat(indexesOn("chat_report"))
          .contains("idx_chat_report_status", "idx_chat_report_sender", "idx_chat_report_reporter");
    }
  }
}
