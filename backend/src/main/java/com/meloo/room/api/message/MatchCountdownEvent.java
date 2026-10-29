package com.meloo.room.api.message;

import java.time.Instant;

/** 30-second matchmaking countdown event (BR-13). */
public record MatchCountdownEvent(String type, int remainingSeconds, Instant serverTime)
    implements RoomMessage {

  public MatchCountdownEvent(int remainingSeconds, Instant serverTime) {
    this("MATCH_COUNTDOWN", remainingSeconds, serverTime);
  }
}
