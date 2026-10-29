package com.meloo.audio.domain;

import java.time.Instant;
import java.util.UUID;

/** Opaque token data bound server-side to (roundId, playerId, level, expiresAt) per ADR-008. */
public record AudioToken(UUID roundId, UUID playerId, int level, Instant expiresAt) {
  public boolean isExpired(Instant now) {
    return now.isAfter(expiresAt);
  }
}
