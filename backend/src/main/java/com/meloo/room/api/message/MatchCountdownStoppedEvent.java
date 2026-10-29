package com.meloo.room.api.message;

import java.time.Instant;

/** Matchmaking countdown stopped because player count dropped below 2 (BR-13). */
public record MatchCountdownStoppedEvent(String type, String reason, Instant serverTime)
    implements RoomMessage {

  public MatchCountdownStoppedEvent(String reason, Instant serverTime) {
    this("MATCH_COUNTDOWN_STOPPED", reason, serverTime);
  }
}
