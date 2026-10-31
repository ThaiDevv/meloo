package com.meloo.identity.api;

import java.util.UUID;

/**
 * Query service for checking whether a player's account is locked (BR-28).
 *
 * <p>Exposed in the {@code identity.api} package as a public SPI for other modules (e.g. {@code
 * audio}) to query account lock status synchronously without directly accessing identity tables
 * (ADR-001).
 *
 * <p><strong>Design &amp; Caching:</strong> No cache is used in this initial design. Each check
 * performs a primary key lookup, protected by existing rate limits (e.g. 30 requests/minute per
 * player for audio streaming). If load testing (QA-03 / k6) shows this is a bottleneck, caching
 * will be addressed in a dedicated ticket. Consequence: account locks take effect immediately on
 * the very next request; previously issued tokens are not revoked, but any subsequent downloads or
 * requests will be blocked.
 */
public interface AccountLockQuery {

  /**
   * Checks whether the user with the given ID is locked.
   *
   * <p>If the user ID does not exist in the database, this method returns {@code false} (does not
   * block). Rationale: audio tokens and session tokens are signed exclusively by the server for
   * valid players; if a record is deleted or missing, the fault lies in data integrity and should
   * not break audio playback.
   *
   * @param userId the unique identifier of the user
   * @return {@code true} if the user exists and has status {@code LOCKED}, {@code false} otherwise
   */
  boolean isLocked(UUID userId);
}
