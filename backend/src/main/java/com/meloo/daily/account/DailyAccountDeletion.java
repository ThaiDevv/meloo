package com.meloo.daily.account;

import com.meloo.audio.api.AudioService;
import com.meloo.identity.events.AccountDeleted;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class DailyAccountDeletion {
  private final JdbcTemplate jdbc;
  private final AudioService audio;

  public DailyAccountDeletion(JdbcTemplate jdbc, AudioService audio) {
    this.jdbc = jdbc;
    this.audio = audio;
  }

  @ApplicationModuleListener
  public void on(AccountDeleted event) {
    var id = event.userId();
    jdbc.query(
        "select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, "daily:player:" + id);
    var attempts =
        jdbc.queryForList("select id from daily_attempt where user_id = ?", UUID.class, id);
    jdbc.update(
        "update daily_attempt set user_id = null, is_guest = true, status = case when status = 'IN_PROGRESS' then 'UNSOLVED' else status end, finished_at = coalesce(finished_at, now()), outcome = coalesce(outcome, 'TIMEOUT') where user_id = ?",
        id);
    jdbc.update("update daily_challenge set set_by = null where set_by = ?", id);
    jdbc.update("delete from daily_guest_merge where guest_id = ? or user_id = ?", id, id);
    attempts.forEach(audio::releaseRoundAudio);
  }
}
