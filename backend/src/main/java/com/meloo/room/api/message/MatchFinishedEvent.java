package com.meloo.room.api.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Final match results with rankings and tie-break milliseconds (BR-10). */
public record MatchFinishedEvent(
    String type, UUID matchId, List<PlayerRankingPayload> rankings, Instant serverTime)
    implements RoomMessage {

  public MatchFinishedEvent(UUID matchId, List<PlayerRankingPayload> rankings, Instant serverTime) {
    this("MATCH_FINISHED", matchId, rankings, serverTime);
  }
}
