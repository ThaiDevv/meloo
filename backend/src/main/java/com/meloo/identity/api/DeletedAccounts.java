package com.meloo.identity.api;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Guards event-driven writes against deletion without exposing identity persistence. */
public interface DeletedAccounts {
  /**
   * Returns deleted IDs and holds shared account locks until the caller's transaction ends. Call
   * inside a transaction before writing user-linked data; deletion takes the exclusive lock.
   */
  Set<UUID> findDeleted(Collection<UUID> ids);
}
