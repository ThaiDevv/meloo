package com.meloo.daily.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class DailyMigrationIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @Test
  void v12UpgradesExistingAttemptsWithoutTakingV11AndKeepsUniqueAttemptConstraint() {
    var dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).target("10").load().migrate();
    var jdbc = new JdbcTemplate(dataSource);
    UUID id = UUID.randomUUID(), user = UUID.randomUUID();
    jdbc.update(
        "insert into daily_challenge(challenge_date, track_id, source) values ('2026-11-10', ?, 'AUTO')",
        UUID.randomUUID());
    jdbc.update(
        "insert into daily_attempt(id, challenge_date, user_id, is_guest, status, started_at, correct_level) values (?, '2026-11-10', ?, true, 'SOLVED', now(), 2)",
        id,
        user);
    Flyway.configure().dataSource(dataSource).load().migrate();
    var row = jdbc.queryForMap("select * from daily_attempt where id = ?", id);
    assertThat(row.get("status")).isEqualTo("SOLVED");
    assertThat(((Number) row.get("current_level")).intValue()).isEqualTo(2);
    assertThat(row.get("outcome")).isEqualTo("CORRECT");
    assertThat(row.get("wrong_count")).isEqualTo(0);
    assertThat(
            jdbc.queryForList(
                "select indexname from pg_indexes where tablename = 'daily_attempt'", String.class))
        .contains("idx_daily_attempt_expiry", "idx_daily_attempt_leaderboard");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into daily_attempt(id, challenge_date, user_id, is_guest, status, started_at) values (?, '2026-11-10', ?, true, 'IN_PROGRESS', now())",
                    UUID.randomUUID(),
                    user))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("update daily_attempt set current_level = 6 where id = ?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    jdbc.update(
        "insert into daily_guest_merge(guest_id, user_id) values (?, ?)", user, UUID.randomUUID());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into daily_guest_merge(guest_id, user_id) values (?, ?)",
                    user,
                    UUID.randomUUID()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }
}
