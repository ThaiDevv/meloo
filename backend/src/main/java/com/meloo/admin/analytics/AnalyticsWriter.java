package com.meloo.admin.analytics;

import com.meloo.admin.config.AdminProperties;
import com.meloo.identity.api.DeletedAccounts;
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
  private final DeletedAccounts deletedAccounts;

  public AnalyticsWriter(
      JdbcTemplate jdbc,
      ObjectMapper json,
      Clock clock,
      AdminProperties properties,
      DeletedAccounts deletedAccounts) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
    this.properties = properties;
    this.deletedAccounts = deletedAccounts;
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
    var ids = new java.util.HashSet<UUID>();
    if (userId != null) ids.add(userId);
    Object target = props.get("userId");
    if (target instanceof UUID id) ids.add(id);
    Object players = props.get("players");
    if (players instanceof java.util.Collection<?> list) {
      list.stream().filter(UUID.class::isInstance).map(UUID.class::cast).forEach(ids::add);
    }
    var deleted = deletedAccounts.findDeleted(ids);
    if (userId != null && deleted.contains(userId)) userId = null;
    if (target instanceof UUID id && deleted.contains(id)) props.remove("userId");
    if (players instanceof java.util.Collection<?> list) {
      props.put("players", list.stream().filter(p -> !deleted.contains(p)).toList());
    }
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
