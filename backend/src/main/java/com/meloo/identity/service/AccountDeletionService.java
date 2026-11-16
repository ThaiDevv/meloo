package com.meloo.identity.service;

import com.meloo.identity.events.AccountDeleted;
import com.meloo.identity.persistence.AppUser;
import com.meloo.identity.persistence.AppUserRepository;
import com.meloo.identity.persistence.UserKind;
import com.meloo.identity.persistence.UserRole;
import com.meloo.identity.persistence.UserStatus;
import com.meloo.shared.api.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionService {
  private final AppUserRepository users;
  private final JdbcTemplate jdbc;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public AccountDeletionService(
      AppUserRepository users, JdbcTemplate jdbc, ApplicationEventPublisher events, Clock clock) {
    this.users = users;
    this.jdbc = jdbc;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public void deleteMyAccount(UUID id) {
    lock(id);
    AppUser user =
        users
            .findByIdForUpdate(id)
            .orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found"));
    if (user.getStatus() == UserStatus.LOCKED || user.getMergedIntoId() != null) {
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED", "Account is locked");
    }
    if (user.getKind() != UserKind.REGISTERED) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "GUEST_CANNOT_DELETE_ACCOUNT", "Guests cannot self-delete");
    }
    erase(user);
  }

  @Transactional
  public void deleteFacebookAccount(String subject) {
    // Resolve inside the transaction; a repeated callback after the link was deleted is a no-op.
    var ids =
        jdbc.queryForList(
            "select user_id from auth_identity where provider = 'FACEBOOK' and provider_subject = ?",
            UUID.class,
            subject);
    for (UUID id : ids) {
      lock(id);
      users.findByIdForUpdate(id).ifPresent(this::erase);
    }
  }

  @Transactional
  public void deleteInactiveGuest(UUID id, Instant cutoff) {
    lock(id);
    users
        .findByIdForUpdate(id)
        .filter(u -> u.getKind() == UserKind.GUEST && u.getLastActiveAt().isBefore(cutoff))
        .ifPresent(this::erase);
  }

  private void lock(UUID id) {
    jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 84))", rs -> {}, id.toString());
  }

  private void erase(AppUser user) {
    if (user.getId().equals(user.getMergedIntoId())) return;
    // Keep an anonymous, permanently inaccessible row for historical references and old JWTs.
    // A self-reference cannot be unmerged or unlocked through the existing account API.
    user.setNickname("Deleted user");
    user.setEmail(null);
    user.setAvatarId(null);
    user.setProfileCompleted(false);
    user.setRole(UserRole.USER);
    user.setStatus(UserStatus.LOCKED);
    user.setLockReason("Account deleted");
    user.setLockedAt(clock.instant());
    user.setMergedIntoId(user.getId());
    jdbc.update("delete from auth_identity where user_id = ?", user.getId());
    // Delete token material, including rotation links, rather than keeping it for 30 more days.
    jdbc.update("update refresh_token set replaced_by_id = null where user_id = ?", user.getId());
    jdbc.update("delete from refresh_token where user_id = ?", user.getId());
    users.flush();
    events.publishEvent(new AccountDeleted(user.getId()));
  }
}
