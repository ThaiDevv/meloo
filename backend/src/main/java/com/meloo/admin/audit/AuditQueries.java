package com.meloo.admin.audit;

import com.meloo.generated.model.AdminAuditLogDto;
import com.meloo.generated.model.AdminAuditLogPageDto;
import com.meloo.shared.api.ApiException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class AuditQueries {
  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper json;

  public AuditQueries(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public AdminAuditLogPageDto list(
      @Nullable UUID actor,
      @Nullable String action,
      @Nullable String targetType,
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime to,
      int page,
      int size) {
    if (page < 0 || size < 1 || size > 50 || (from != null && to != null && from.isAfter(to))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid audit query");
    }
    var filters = new ArrayList<String>();
    var params = new MapSqlParameterSource();
    if (actor != null) {
      filters.add("actor_id = :actor");
      params.addValue("actor", actor);
    }
    if (action != null) {
      filters.add("action = :action");
      params.addValue("action", action);
    }
    if (targetType != null) {
      filters.add("target_type = :type");
      params.addValue("type", targetType);
    }
    if (from != null) {
      filters.add("created_at >= :from");
      params.addValue("from", Timestamp.from(from.toInstant()));
    }
    if (to != null) {
      filters.add("created_at <= :to");
      params.addValue("to", Timestamp.from(to.toInstant()));
    }
    String where = filters.isEmpty() ? "" : " where " + String.join(" and ", filters);
    Long total = jdbc.queryForObject("select count(*) from audit_log" + where, params, Long.class);
    params.addValue("limit", size).addValue("offset", (long) page * size);
    var items =
        jdbc.query(
            "select * from audit_log"
                + where
                + " order by created_at desc, id desc limit :limit offset :offset",
            params,
            (rs, i) ->
                new AdminAuditLogDto(
                        rs.getLong("id"),
                        rs.getObject("actor_id", UUID.class),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getString("reason"),
                        rs.getString("detail") == null
                            ? null
                            : json.readValue(
                                rs.getString("detail"),
                                new TypeReference<Map<String, Object>>() {}),
                        rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC))
                    .targetId(rs.getString("target_id")));
    long count = total == null ? 0 : total;
    return new AdminAuditLogPageDto()
        .items(items)
        .page(page)
        .size(size)
        .totalItems(count)
        .totalPages(Math.toIntExact((count + size - 1) / size));
  }
}
