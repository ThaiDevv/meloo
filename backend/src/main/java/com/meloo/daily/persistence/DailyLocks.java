package com.meloo.daily.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Transaction-scoped locks cover absent rows too (first start and asynchronous guest merge). */
@Repository
public class DailyLocks {
  private final JdbcTemplate jdbc;

  public DailyLocks(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void acquire(String key) {
    jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, "daily:" + key);
  }

  public boolean merged(UUID guestId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from daily_guest_merge where guest_id = ?)",
            Boolean.class,
            guestId));
  }

  public void markMerged(UUID guestId, UUID userId) {
    jdbc.update("insert into daily_guest_merge(guest_id, user_id) values (?, ?)", guestId, userId);
  }
}
