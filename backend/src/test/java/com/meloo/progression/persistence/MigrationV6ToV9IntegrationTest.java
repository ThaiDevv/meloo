package com.meloo.progression.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MigrationV6ToV9IntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @ParameterizedTest
  @ValueSource(ints = {0, 5})
  void migratesEmptyDatabaseAndV5WithoutLosingData(int initialVersion) {
    String schema = "public";
    String database = "migration_" + UUID.randomUUID().toString().replace("-", "");
    var dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("create database " + database);
    // Separate databases isolate extensions as well as Flyway history.
    dataSource.setUrl(
        POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database));
    if (initialVersion == 5) {
      flyway(dataSource, schema, "5").migrate();
      jdbc.update(
          "insert into chat_report (id, message_text, sender_id, sender_nickname, reporter_id) values (?, 'retained', ?, 'player', ?)",
          UUID.randomUUID(),
          UUID.randomUUID(),
          UUID.randomUUID());
    }
    assertThat(flyway(dataSource, schema, "9").migrate().migrationsExecuted)
        .isEqualTo(initialVersion == 5 ? 4 : 9);
    assertThat(flyway(dataSource, schema, "9").info().current().getVersion())
        .isEqualTo(MigrationVersion.fromVersion("9"));
    assertThat(jdbc.queryForList("select code from achievement order by sort_order", String.class))
        .containsExactly("FIRST_WIN", "LIGHTNING_EAR", "COMBO_5", "DAILY_STREAK_7");
    assertThat(
            jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = ?",
                String.class,
                schema))
        .contains(
            "player_stats",
            "achievement",
            "user_achievement",
            "daily_challenge",
            "daily_attempt",
            "audit_log",
            "analytics_event",
            "chat_report",
            "banned_word");
    if (initialVersion == 5) {
      assertThat(jdbc.queryForObject("select message_text from chat_report", String.class))
          .isEqualTo("retained");
    }
    var indexes =
        jdbc.queryForMap(
            "select indexname, indexdef from pg_indexes where schemaname = ? and indexname = 'idx_player_stats_leaderboard'",
            schema);
    assertThat(indexes.get("indexdef").toString())
        .contains("ranked_score DESC", "ranked_reached_at", "WHERE (NOT is_guest)");
    assertThat(
            jdbc.queryForList(
                "select indexname from pg_indexes where schemaname = ?", String.class, schema))
        .contains(
            "idx_match_player_user_matches",
            "idx_match_player_leaderboard",
            "idx_user_achievement_code",
            "idx_daily_attempt_leaderboard",
            "idx_daily_attempt_user_dates",
            "idx_audit_log_created",
            "idx_audit_log_actor",
            "idx_audit_log_target",
            "idx_analytics_event_type_occurred",
            "idx_analytics_event_occurred",
            "idx_analytics_event_user");
    UUID user = UUID.randomUUID();
    jdbc.update("insert into player_stats (user_id, is_guest) values (?, false)", user);
    assertThat(
            jdbc.queryForObject(
                "select ranked_score from player_stats where user_id = ?", Long.class, user))
        .isZero();
    assertThatThrownBy(
            () -> jdbc.update("update player_stats set ranked_score = -1 where user_id = ?", user))
        .hasMessageContaining("player_stats_ranked_score_check");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into user_achievement (user_id, achievement_code, unlocked_at) values (?, 'MISSING', now())",
                    user))
        .hasMessageContaining("user_achievement_achievement_code_fkey");
    jdbc.update(
        "insert into user_achievement (user_id, achievement_code, unlocked_at) values (?, 'FIRST_WIN', now())",
        user);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into user_achievement (user_id, achievement_code, unlocked_at) values (?, 'FIRST_WIN', now())",
                    user))
        .hasMessageContaining("user_achievement_pkey");
    jdbc.update(
        "insert into daily_challenge (challenge_date, track_id, source) values (date '2026-11-09', ?, 'AUTO')",
        UUID.randomUUID());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into daily_challenge (challenge_date, track_id, source) values (date '2026-11-10', ?, 'INVALID')",
                    UUID.randomUUID()))
        .hasMessageContaining("daily_challenge_source_check");
    jdbc.update(
        "insert into daily_attempt (id, challenge_date, user_id, is_guest, status, started_at) values (?, date '2026-11-09', ?, false, 'IN_PROGRESS', now())",
        UUID.randomUUID(),
        user);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into daily_attempt (id, challenge_date, user_id, is_guest, status, started_at) values (?, date '2026-11-09', ?, false, 'IN_PROGRESS', now())",
                    UUID.randomUUID(),
                    user))
        .hasMessageContaining("daily_attempt_challenge_date_user_id_key");
    assertThatThrownBy(() -> jdbc.update("update daily_attempt set correct_level = 6"))
        .hasMessageContaining("daily_attempt_correct_level_check");
    assertThatThrownBy(() -> jdbc.update("update daily_attempt set status = 'INVALID'"))
        .hasMessageContaining("daily_attempt_status_check");
    assertThatThrownBy(
            () -> jdbc.update("update daily_attempt set challenge_date = date '2020-01-01'"))
        .hasMessageContaining("daily_attempt_challenge_date_fkey");
    jdbc.update(
        "insert into audit_log (actor_id, action, target_type) values (?, 'USER_LOCK', 'USER')",
        user);
    for (String sql :
        new String[] {
          "update audit_log set reason = 'changed'", "delete from audit_log", "truncate audit_log"
        }) {
      assertThatThrownBy(() -> jdbc.execute(sql)).hasMessageContaining("audit_log is append-only");
    }
    jdbc.update(
        "insert into analytics_event (event_type, user_id, props, occurred_at) values ('USER_REGISTERED', ?, '{\"source\":\"google\"}', now())",
        user);
    jdbc.update("update analytics_event set user_id = null where user_id = ?", user);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from analytics_event where user_id is null", Integer.class))
        .isEqualTo(1);
    jdbc.update("delete from analytics_event where occurred_at < now() - interval '90 days'");
  }

  @Test
  void upgradesExistingV10DatabaseWithExplicitOutOfOrderMigration(@TempDir Path oldMigrations)
      throws IOException {
    for (String file :
        new String[] {
          "V1__baseline.sql",
          "V2__identity_init.sql",
          "V3__catalog_init.sql",
          "V4__progression_match_history.sql",
          "V5__chat_init.sql",
          "V10__catalog_track_cover_url.sql"
        }) {
      try (var source = getClass().getResourceAsStream("/db/migration/" + file)) {
        Files.copy(java.util.Objects.requireNonNull(source), oldMigrations.resolve(file));
      }
    }
    var admin =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    String database = "migration_" + UUID.randomUUID().toString().replace("-", "");
    new JdbcTemplate(admin).execute("create database " + database);
    var dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    Flyway.configure()
        .dataSource(dataSource)
        .locations("filesystem:" + oldMigrations)
        .load()
        .migrate();
    assertThatThrownBy(() -> Flyway.configure().dataSource(dataSource).load().migrate())
        .hasMessageContaining("outOfOrder");
    assertThat(
            Flyway.configure()
                .dataSource(dataSource)
                .outOfOrder(true)
                .load()
                .migrate()
                .migrationsExecuted)
        .isEqualTo(4);
    assertThat(
            new JdbcTemplate(dataSource)
                .queryForObject("select count(*) from achievement", Integer.class))
        .isEqualTo(4);
    assertThat(
            Flyway.configure()
                .dataSource(dataSource)
                .load()
                .validateWithResult()
                .validationSuccessful)
        .isTrue();
  }

  private Flyway flyway(DriverManagerDataSource dataSource, String schema, String target) {
    return Flyway.configure()
        .dataSource(dataSource)
        .schemas(schema)
        .defaultSchema(schema)
        .locations("classpath:db/migration")
        .target(target)
        .load();
  }
}
