package com.meloo.admin.account;

import com.meloo.identity.events.AccountDeleted;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountDeletion {
  private final JdbcTemplate jdbc;

  public AdminAccountDeletion(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @ApplicationModuleListener
  public void on(AccountDeleted event) {
    var id = event.userId();
    // Uses DES-01's partial index, preserving analytics counts and timestamps.
    jdbc.update("update analytics_event set user_id = null where user_id = ?", id);
    jdbc.update(
        "update analytics_event set props = props - 'userId' where props ->> 'userId' = ?",
        id.toString());
    jdbc.update(
        "update analytics_event set props = jsonb_set(props, '{players}', (props -> 'players') - ?) where jsonb_typeof(props -> 'players') = 'array' and jsonb_exists(props -> 'players', ?)",
        id.toString(),
        id.toString());
    // Audit retention remains governed by the append-only constraint in V8.
  }
}
