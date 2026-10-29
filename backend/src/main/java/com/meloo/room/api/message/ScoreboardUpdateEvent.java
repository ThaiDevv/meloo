package com.meloo.room.api.message;

import java.time.Instant;
import java.util.List;

/** Realtime scoreboard update after each round. */
public record ScoreboardUpdateEvent(
    String type, List<PlayerScoreSummaryPayload> scores, Instant serverTime)
    implements RoomMessage {

  public ScoreboardUpdateEvent(List<PlayerScoreSummaryPayload> scores, Instant serverTime) {
    this("SCOREBOARD_UPDATE", scores, serverTime);
  }
}
