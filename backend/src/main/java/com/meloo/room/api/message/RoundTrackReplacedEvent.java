package com.meloo.room.api.message;

import java.time.Instant;

/** Track replaced mid-match due to audio source failure (BR-27). */
public record RoundTrackReplacedEvent(String type, int roundNo, String reason, Instant serverTime)
    implements RoomMessage {

  public RoundTrackReplacedEvent(int roundNo, String reason, Instant serverTime) {
    this("ROUND_TRACK_REPLACED", roundNo, reason, serverTime);
  }
}
