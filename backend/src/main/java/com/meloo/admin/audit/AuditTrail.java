package com.meloo.admin.audit;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditTrail {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final Clock clock;

  public AuditTrail(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public void append(
      UUID actor,
      String action,
      String targetType,
      @Nullable String targetId,
      @Nullable String reason,
      @Nullable Object before,
      @Nullable Object after) {
    var detail = json.createObjectNode();
    detail.set("before", json.valueToTree(before));
    detail.set("after", json.valueToTree(after));
    jdbc.update(
        """
        insert into audit_log(actor_id, action, target_type, target_id, reason, detail, created_at)
        values (?, ?, ?, ?, ?, cast(? as jsonb), ?)
        """,
        actor,
        action,
        targetType,
        targetId,
        reason,
        json.writeValueAsString(detail),
        Timestamp.from(clock.instant()));
  }
}
