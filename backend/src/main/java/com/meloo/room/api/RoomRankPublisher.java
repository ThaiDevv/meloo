package com.meloo.room.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Enqueues committed rank results after the room's current MATCH_FINISHED command. */
public interface RoomRankPublisher {
  CompletableFuture<Void> publish(
      UUID roomId, UUID matchId, List<Change> changes, Instant serverTime);

  record Change(UUID userId, int rankDelta, int rankPoints) {}
}
