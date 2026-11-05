package com.meloo.room.match;

import java.util.UUID;

/** Internal synchronous notification; always published on the room actor queue. */
public record RoomMembershipChanged(UUID roomId, UUID userId, Change change) {
  public enum Change {
    CONNECTED,
    DISCONNECTED,
    LEFT
  }
}
