package com.meloo.room.api.message;

import java.time.Instant;
import java.util.UUID;

/** A player unlocked the next listening level (audio token delivered privately). */
public record ListenMoreOpenedEvent(String type, UUID userId, int level, Instant serverTime)
    implements RoomMessage {

  public ListenMoreOpenedEvent(UUID userId, int level, Instant serverTime) {
    this("LISTEN_MORE_OPENED", userId, level, serverTime);
  }
}
