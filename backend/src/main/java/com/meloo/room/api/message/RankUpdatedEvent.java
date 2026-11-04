package com.meloo.room.api.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Rank points delta and new totals for quick-match participants (BR-13, BR-24). */
public record RankUpdatedEvent(
    String type, UUID matchId, List<PlayerRankChangePayload> changes, Instant serverTime)
    implements RoomMessage {

  public RankUpdatedEvent(UUID matchId, List<PlayerRankChangePayload> changes, Instant serverTime) {
    this("RANK_UPDATED", matchId, changes, serverTime);
  }
}
