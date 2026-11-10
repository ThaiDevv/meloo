package com.meloo.admin.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.meloo.admin.config.AdminProperties;
import com.meloo.identity.api.UserAccounts;
import jakarta.validation.Validation;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AdminAnalyticsTest {
  @Test
  void retentionUsesConfiguredDaysAndInjectedClock() {
    var jdbc = mock(JdbcTemplate.class);
    var clock = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC);
    var props =
        new AdminProperties(
            7,
            "0 0 3 * * *",
            null,
            LocalDate.parse("2026-11-29"),
            LocalDate.parse("2026-11-27"),
            Set.of());
    new AnalyticsRetentionJob(jdbc, clock, props).purge();
    verify(jdbc)
        .update(
            "delete from analytics_event where occurred_at < ?",
            Timestamp.from(Instant.parse("2026-11-03T00:00:00Z")));
  }

  @Test
  void betaDateOrderAndRetentionAreValidated() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var invalid =
          new AdminProperties(
              0,
              "",
              LocalDate.parse("2026-11-28"),
              LocalDate.parse("2026-11-29"),
              LocalDate.parse("2026-11-27"),
              Set.of());
      assertThat(factory.getValidator().validate(invalid)).hasSize(3);
      var valid =
          new AdminProperties(
              90,
              "0 0 3 * * *",
              null,
              LocalDate.parse("2026-11-29"),
              LocalDate.parse("2026-11-27"),
              Set.of());
      assertThat(factory.getValidator().validate(valid)).isEmpty();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void returningMetricIsUnavailableUntilBetaOpeningDateIsSet() {
    var jdbc = mock(NamedParameterJdbcTemplate.class);
    var users = mock(UserAccounts.class);
    when(users.search("", 0, 100)).thenReturn(new UserAccounts.UserPage(List.of(), 0, 0, 100));
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(0L);
    when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(new AnalyticsQueries.ReturningPlayers(0, 0));
    var properties =
        new AdminProperties(
            90,
            "0 0 3 * * *",
            null,
            LocalDate.parse("2026-11-29"),
            LocalDate.parse("2026-11-27"),
            Set.of());
    var result = new AnalyticsQueries(jdbc, users, properties, Clock.systemUTC()).snapshot();
    assertThat(result.returningPlayers()).isNull();
    assertThat(result.guestConversion().rate()).isNull();
  }
}
