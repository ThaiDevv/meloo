package com.meloo.room.api.message;

import java.time.Instant;

/** 45-second round clock starts (BR-07). */
public record RoundStartEvent(String type, int roundNo, int durationMs, Instant serverTime)
    implements RoomMessage {

  public RoundStartEvent(int roundNo, int durationMs, Instant serverTime) {
    this("ROUND_START", roundNo, durationMs, serverTime);
  }
}
