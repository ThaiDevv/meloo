package com.meloo.room.api.message;

import java.time.Instant;
import java.util.UUID;

/** Broadcast when a player changes ready status. */
public record PlayerReadyChangedEvent(String type, UUID userId, boolean isReady, Instant serverTime)
    implements RoomMessage {

  public PlayerReadyChangedEvent(UUID userId, boolean isReady, Instant serverTime) {
    this("PLAYER_READY_CHANGED", userId, isReady, serverTime);
  }
}
