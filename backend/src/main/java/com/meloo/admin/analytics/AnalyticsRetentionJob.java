package com.meloo.admin.analytics;

import com.meloo.admin.config.AdminProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsRetentionJob {
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final AdminProperties properties;

  public AnalyticsRetentionJob(JdbcTemplate jdbc, Clock clock, AdminProperties properties) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.properties = properties;
  }

  @Scheduled(cron = "${meloo.admin.retention-cron}", zone = "Asia/Ho_Chi_Minh")
  @Transactional
  public int purge() {
    return jdbc.update(
        "delete from analytics_event where occurred_at < ?",
        Timestamp.from(clock.instant().minus(properties.retentionDays(), ChronoUnit.DAYS)));
  }
}
