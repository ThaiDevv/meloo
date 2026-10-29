package com.meloo.audio.api;

import java.util.UUID;

/**
 * SPI for checking whether a player's account has been locked (BR-28).
 *
 * <p>Exposed in the {@code audio.api} package so other modules (e.g. {@code identity}) can provide
 * implementations without violating Spring Modulith package encapsulation.
 */
@FunctionalInterface
public interface AccountLockChecker {

  /**
   * Returns {@code true} if the player account is locked.
   *
   * @param playerId the unique identifier of the player
   * @return true if locked, false otherwise
   */
  boolean isAccountLocked(UUID playerId);
}
