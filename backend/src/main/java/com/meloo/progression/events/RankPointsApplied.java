package com.meloo.progression.events;

import com.meloo.room.api.RoomRankPublisher.Change;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persisted by the Event Publication Registry in the same transaction as the rank changes. */
public record RankPointsApplied(
    UUID roomId, UUID matchId, List<Change> changes, Instant appliedAt) {
  public RankPointsApplied {
    changes = List.copyOf(changes);
  }
}
