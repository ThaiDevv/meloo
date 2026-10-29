package com.meloo.audio.service;

import java.util.UUID;

/** Service provider interface for checking account lock status per BR-28. */
@FunctionalInterface
public interface AccountLockChecker {

  boolean isAccountLocked(UUID playerId);
}
