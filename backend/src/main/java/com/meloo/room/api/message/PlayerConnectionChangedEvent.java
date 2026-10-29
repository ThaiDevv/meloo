package com.meloo.room.api.message;

import java.time.Instant;
import java.util.UUID;

/** Broadcast when a player disconnects temporarily or reconnects within 30s (BR-14). */
public record PlayerConnectionChangedEvent(
    String type, UUID userId, boolean isConnected, Instant serverTime) implements RoomMessage {

  public PlayerConnectionChangedEvent(UUID userId, boolean isConnected, Instant serverTime) {
    this("PLAYER_CONNECTION_CHANGED", userId, isConnected, serverTime);
  }
}
