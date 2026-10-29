package com.meloo.room.api.message;

import java.time.Instant;

/** Prepare phase without broadcasting audio token (ADR-008). */
public record RoundPrepareEvent(
    String type, int roundNo, int totalRounds, int timeoutMs, Instant serverTime)
    implements RoomMessage {

  public RoundPrepareEvent(int roundNo, int totalRounds, int timeoutMs, Instant serverTime) {
    this("ROUND_PREPARE", roundNo, totalRounds, timeoutMs, serverTime);
  }
}
