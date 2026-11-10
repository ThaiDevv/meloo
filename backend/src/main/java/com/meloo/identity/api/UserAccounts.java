package com.meloo.identity.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Internal application API. The admin module must authorize and audit its callers. */
public interface UserAccounts {
  UserAccount get(UUID id);

  /** Active registered accounts that have not been merged, fetched in bulk. */
  Map<UUID, UserAccount> findByIdIn(Collection<UUID> ids);

  /** Updates an active registered player's display name after BR-22 validation. */
  UserAccount updateNickname(UUID id, String nickname);

  UserPage search(String query, int page, int size);

  void setLocked(UUID id, boolean locked, String reason);

  void setRole(UUID id, String role);

  long countCreated(String kind, Instant fromInclusive, Instant toExclusive);

  record UserPage(List<UserAccount> items, long total, int page, int size) {
    public UserPage {
      items = List.copyOf(items);
    }
  }
}
