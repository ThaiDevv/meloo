package com.meloo.progression.account;

import com.meloo.identity.events.AccountDeleted;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class ProgressionAccountDeletion {
  private final JdbcTemplate jdbc;

  public ProgressionAccountDeletion(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @ApplicationModuleListener
  public void on(AccountDeleted event) {
    jdbc.execute("select pg_advisory_xact_lock(68068)");
    var id = event.userId();
    jdbc.update(
        "update match_player set user_id = null, nickname = 'Deleted user' where user_id = ? or owner_id = ?",
        id,
        id);
    jdbc.update(
        "update match_player set owner_id = null, counts_for_leaderboard = false where owner_id = ?",
        id);
    jdbc.update("delete from player_stats where user_id = ?", id);
    jdbc.update("delete from user_achievement where user_id = ?", id);
    jdbc.update("delete from progression_guest_merge where guest_id = ? or user_id = ?", id, id);
  }
}
