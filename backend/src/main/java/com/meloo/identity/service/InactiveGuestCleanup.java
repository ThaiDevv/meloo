package com.meloo.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InactiveGuestCleanup {
  private final JdbcTemplate jdbc;
  private final AccountDeletionService deletion;
  private final Clock clock;

  public InactiveGuestCleanup(JdbcTemplate jdbc, AccountDeletionService deletion, Clock clock) {
    this.jdbc = jdbc;
    this.deletion = deletion;
    this.clock = clock;
  }

  @Scheduled(cron = "${meloo.identity.guest-cleanup-cron:0 0 3 * * *}", zone = "UTC")
  public void run() {
    var cutoff = clock.instant().minus(Duration.ofDays(30));
    while (true) {
      var ids =
          jdbc.queryForList(
              "select id from app_user where kind = 'GUEST' and last_active_at < ? and (merged_into_id is null or merged_into_id <> id) order by last_active_at, id limit 200",
              UUID.class,
              java.sql.Timestamp.from(cutoff));
      if (ids.isEmpty()) return;
      // Each account is a separate transaction, with activity rechecked under its row lock.
      for (UUID id : ids) deletion.deleteInactiveGuest(id, cutoff);
    }
  }
}
