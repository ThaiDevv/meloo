package com.meloo.room.api.message;

import java.time.Instant;
import java.util.UUID;

/** Player gave up at the 30s mark (BR-04). */
public record PlayerGaveUpEvent(String type, UUID userId, int roundNo, Instant serverTime)
    implements RoomMessage {

  public PlayerGaveUpEvent(UUID userId, int roundNo, Instant serverTime) {
    this("PLAYER_GAVE_UP", userId, roundNo, serverTime);
  }
}
