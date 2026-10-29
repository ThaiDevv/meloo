package com.meloo.room.api.message;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Broadcast when a player leaves or disconnects (BR-12). */
public record PlayerLeftEvent(
    String type, UUID userId, @Nullable UUID newHostId, String reason, Instant serverTime)
    implements RoomMessage {

  public PlayerLeftEvent(UUID userId, @Nullable UUID newHostId, String reason, Instant serverTime) {
    this("PLAYER_LEFT", userId, newHostId, reason, serverTime);
  }
}
