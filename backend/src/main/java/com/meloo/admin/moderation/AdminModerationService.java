package com.meloo.admin.moderation;

import com.meloo.admin.audit.AuditTrail;
import com.meloo.chat.api.*;
import com.meloo.identity.api.UserAccount;
import com.meloo.identity.api.UserAccounts;
import com.meloo.shared.api.ApiException;
import java.text.Normalizer;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminModerationService {
  private final UserAccounts users;
  private final ChatAdminApi chat;
  private final AuditTrail audit;
  private final JdbcTemplate jdbc;

  public AdminModerationService(
      UserAccounts users, ChatAdminApi chat, AuditTrail audit, JdbcTemplate jdbc) {
    this.users = users;
    this.chat = chat;
    this.audit = audit;
    this.jdbc = jdbc;
  }

  public UserAccount lock(UUID actor, UUID id, boolean locked, @Nullable String reason) {
    if (actor.equals(id) && locked) throw selfAction();
    if (locked && (reason == null || reason.isBlank())) throw invalid("A lock reason is required");
    serialize("user", id);
    var before = users.get(id);
    users.setLocked(id, locked, reason == null ? "" : reason);
    var after = users.get(id);
    audit.append(
        actor,
        locked ? "USER_LOCKED" : "USER_UNLOCKED",
        "USER",
        id.toString(),
        locked ? after.lockReason() : null,
        before,
        after);
    return after;
  }

  public UserAccount role(UUID actor, UUID id, String role) {
    if (actor.equals(id) && !"ADMIN".equals(role)) throw selfAction();
    serialize("user", id);
    var before = users.get(id);
    if (!"REGISTERED".equals(before.kind()))
      throw invalid("Only registered accounts can change role");
    users.setRole(id, role);
    var after = users.get(id);
    audit.append(actor, "USER_ROLE_CHANGED", "USER", id.toString(), null, before, after);
    return after;
  }

  public ChatReportDto resolve(
      UUID actor, UUID id, ReportStatus status, boolean lockSender, @Nullable String reason) {
    if (lockSender && status != ReportStatus.ACTIONED)
      throw invalid("Locking requires ACTIONED status");
    serialize("report", id);
    var before = chat.findReportById(id).orElseThrow(() -> missing("REPORT_NOT_FOUND"));
    if (lockSender) lock(actor, before.senderId(), true, reason);
    var after = chat.handleReport(id, status, actor);
    audit.append(
        actor, "CHAT_REPORT_RESOLVED", "CHAT_REPORT", id.toString(), reason, before, after);
    return after;
  }

  public BannedWordDto word(UUID actor, @Nullable UUID id, String word) {
    if (id != null) serialize("word", id);
    var before =
        id == null
            ? null
            : chat.findBannedWordById(id).orElseThrow(() -> missing("WORD_NOT_FOUND"));
    String normalized = Normalizer.normalize(word, Normalizer.Form.NFC);
    var after =
        id == null ? chat.addBannedWord(normalized, actor) : chat.updateBannedWord(id, normalized);
    audit.append(
        actor,
        id == null ? "BANNED_WORD_CREATED" : "BANNED_WORD_UPDATED",
        "BANNED_WORD",
        after.id().toString(),
        null,
        before,
        after);
    return after;
  }

  public void deleteWord(UUID actor, UUID id) {
    serialize("word", id);
    var before = chat.findBannedWordById(id).orElseThrow(() -> missing("WORD_NOT_FOUND"));
    chat.deleteBannedWord(id);
    audit.append(actor, "BANNED_WORD_DELETED", "BANNED_WORD", id.toString(), null, before, null);
  }

  // Serialize admin snapshots and mutations across instances without reaching into other modules.
  private void serialize(String type, UUID id) {
    jdbc.query(
        "select pg_advisory_xact_lock(hashtextextended(?, 0))",
        rs -> {},
        "admin:" + type + ":" + id);
  }

  private static ApiException selfAction() {
    return new ApiException(
        HttpStatus.CONFLICT,
        "ADMIN_SELF_ACTION_FORBIDDEN",
        "Cannot lock or demote your own account");
  }

  private static ApiException invalid(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
  }

  private static ApiException missing(String code) {
    return new ApiException(HttpStatus.NOT_FOUND, code, "Moderation target not found");
  }
}
