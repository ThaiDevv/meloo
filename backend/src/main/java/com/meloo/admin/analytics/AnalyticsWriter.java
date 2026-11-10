package com.meloo.admin.analytics;

import com.meloo.admin.config.AdminProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class AnalyticsWriter {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final Clock clock;
  private final AdminProperties properties;

  public AnalyticsWriter(
      JdbcTemplate jdbc, ObjectMapper json, Clock clock, AdminProperties properties) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
    this.properties = properties;
  }

  @Transactional
  public void record(
      UUID eventId,
      String type,
      @Nullable UUID userId,
      Instant occurredAt,
      Map<String, ?> details) {
    // Expired publication replays cannot reintroduce rows purged by retention.
    if (occurredAt.isBefore(clock.instant().minus(properties.retentionDays(), ChronoUnit.DAYS)))
      return;
    // V9 has no unique event-id column. A transaction-scoped PostgreSQL lock serializes the
    // check/insert across processes without changing the migration owned by DB-02.
    jdbc.query(
        "select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, "admin:" + eventId);
    Boolean exists =
        jdbc.queryForObject(
            "select exists(select 1 from analytics_event where props ->> 'eventId' = ?)",
            Boolean.class,
            eventId.toString());
    if (Boolean.TRUE.equals(exists)) return;
    var props = new HashMap<String, Object>(details);
    props.put("eventId", eventId);
    jdbc.update(
        """
        insert into analytics_event(event_type, user_id, props, occurred_at, created_at)
        values (?, ?, cast(? as jsonb), ?, ?)
        """,
        type,
        userId,
        json.writeValueAsString(props),
        Timestamp.from(occurredAt),
        Timestamp.from(clock.instant()));
  }
}
