package com.meloo.room.api.message;

import java.time.Instant;

/** Broadcast starting countdown before entering round 1. */
public record MatchStartingEvent(String type, int countdownSeconds, Instant serverTime)
    implements RoomMessage {

  public MatchStartingEvent(int countdownSeconds, Instant serverTime) {
    this("MATCH_STARTING", countdownSeconds, serverTime);
  }
}
