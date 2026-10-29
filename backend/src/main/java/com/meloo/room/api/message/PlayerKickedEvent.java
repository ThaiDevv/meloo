package com.meloo.room.api.message;

import java.time.Instant;
import java.util.UUID;

/** Broadcast when host kicks a player (BR-12). */
public record PlayerKickedEvent(String type, UUID userId, Instant serverTime)
    implements RoomMessage {

  public PlayerKickedEvent(UUID userId, Instant serverTime) {
    this("PLAYER_KICKED", userId, serverTime);
  }
}
