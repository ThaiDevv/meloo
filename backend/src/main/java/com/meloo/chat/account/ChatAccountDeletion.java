package com.meloo.chat.account;

import com.meloo.chat.service.ChatHistoryManager;
import com.meloo.identity.events.AccountDeleted;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class ChatAccountDeletion {
  private static final UUID ANONYMOUS = new UUID(0, 0);
  private final JdbcTemplate jdbc;
  private final ChatHistoryManager history;

  public ChatAccountDeletion(JdbcTemplate jdbc, ChatHistoryManager history) {
    this.jdbc = jdbc;
    this.history = history;
  }

  @ApplicationModuleListener
  public void on(AccountDeleted event) {
    var id = event.userId();
    jdbc.update(
        "update chat_report set sender_id = ?, sender_nickname = 'Deleted user', message_text = '[deleted]' where sender_id = ?",
        ANONYMOUS,
        id);
    jdbc.update("update chat_report set reporter_id = ? where reporter_id = ?", ANONYMOUS, id);
    jdbc.update("update chat_report set handled_by = null where handled_by = ?", id);
    jdbc.update("update banned_word set created_by = null where created_by = ?", id);
    jdbc.update(
        """
        update chat_report r set context = (select coalesce(jsonb_agg(m.value order by m.n), '[]'::jsonb)
          from jsonb_array_elements(r.context) with ordinality m(value, n)
          where m.value ->> 'senderId' is distinct from ?)
        where exists (select 1 from jsonb_array_elements(r.context) m where m ->> 'senderId' = ?)
        """,
        id.toString(),
        id.toString());
    // Only a cache-wide eviction API exists; discard cached text so future reports cannot copy it.
    history.clearAll();
  }
}
