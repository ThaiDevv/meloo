package com.meloo.progression.service;

import com.meloo.progression.api.RankPointsQuery;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminRankQueries implements RankPointsQuery {
  private final NamedParameterJdbcTemplate jdbc;

  public AdminRankQueries(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Map<UUID, Integer> findAll(Collection<UUID> userIds) {
    if (userIds.isEmpty()) return Map.of();
    var result = new HashMap<UUID, Integer>();
    userIds.forEach(id -> result.put(id, 0));
    jdbc.query(
        "select user_id, ranked_score from player_stats where user_id in (:ids)",
        Map.of("ids", userIds),
        rs -> {
          result.put(
              rs.getObject("user_id", UUID.class), Math.toIntExact(rs.getLong("ranked_score")));
        });
    return Map.copyOf(result);
  }
}
