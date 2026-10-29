package com.meloo.room.api.message;

import java.time.Instant;
import java.util.UUID;

/** Match ended early because only 1 player remains connected (BR-14). */
public record MatchEarlyEndedEvent(
    String type, String reason, UUID remainingUserId, Instant serverTime) implements RoomMessage {

  public MatchEarlyEndedEvent(String reason, UUID remainingUserId, Instant serverTime) {
    this("MATCH_EARLY_ENDED", reason, remainingUserId, serverTime);
  }
}
