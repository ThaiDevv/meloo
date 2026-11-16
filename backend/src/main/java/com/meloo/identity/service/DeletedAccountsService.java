package com.meloo.identity.service;

import com.meloo.identity.api.DeletedAccounts;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeletedAccountsService implements DeletedAccounts {
  private final JdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate named;

  public DeletedAccountsService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    this.named = new NamedParameterJdbcTemplate(jdbc);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
  public Set<UUID> findDeleted(Collection<UUID> ids) {
    var ordered = ids.stream().distinct().sorted().toList();
    if (ordered.isEmpty()) return Set.of();
    for (var id : ordered)
      jdbc.query(
          "select pg_advisory_xact_lock_shared(hashtextextended(?, 84))", rs -> {}, id.toString());
    // A scalar projection avoids populating the caller's JPA persistence context with users
    // whose status/profile may be updated by other operations in the same transaction.
    return Set.copyOf(
        named.queryForList(
            "select id from app_user where id in (:ids) and merged_into_id = id",
            Map.of("ids", ordered),
            UUID.class));
  }
}
