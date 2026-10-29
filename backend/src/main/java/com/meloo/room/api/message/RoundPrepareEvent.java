package com.meloo.room.api.message;

import java.time.Instant;

/** Prepare phase with audio clip preload (ADR-008). */
public record RoundPrepareEvent(
    String type,
    int roundNo,
    int totalRounds,
    AudioClipPayload audioClip,
    int timeoutMs,
    Instant serverTime)
    implements RoomMessage {

  public RoundPrepareEvent(
      int roundNo, int totalRounds, AudioClipPayload audioClip, int timeoutMs, Instant serverTime) {
    this("ROUND_PREPARE", roundNo, totalRounds, audioClip, timeoutMs, serverTime);
  }
}
