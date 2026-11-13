package com.meloo.identity.persistence;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MigrationV13IntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @Test
  void legacyAccountsIncludingPlayerPrefixAndGuestsRemainCompletedAndPreserveData() {
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    var jdbc = new JdbcTemplate(source);
    Flyway.configure().dataSource(source).target("12").load().migrate();
    UUID registered = UUID.randomUUID(), guest = UUID.randomUUID();
    jdbc.update(
        "insert into app_user(id,kind,nickname) values (?,'REGISTERED','Player_Legacy'),(?,'GUEST','Guest_Legacy')",
        registered,
        guest);
    Flyway.configure().dataSource(source).target("13").load().migrate();
    for (UUID id : java.util.List.of(registered, guest)) {
      assertThat(
              jdbc.queryForObject(
                  "select profile_completed from app_user where id=?", Boolean.class, id))
          .isTrue();
      assertThat(jdbc.queryForObject("select avatar_id from app_user where id=?", String.class, id))
          .isNull();
    }
    assertThat(
            jdbc.queryForObject(
                "select nickname from app_user where id=?", String.class, registered))
        .isEqualTo("Player_Legacy");
    assertThatThrownBy(
            () -> jdbc.update("update app_user set avatar_id='invalid' where id=?", registered))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("update app_user set profile_completed=null where id=?", registered))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
