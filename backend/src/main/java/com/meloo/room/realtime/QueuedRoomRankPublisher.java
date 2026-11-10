package com.meloo.room.realtime;

import com.meloo.room.api.RoomRankPublisher;
import com.meloo.room.api.message.PlayerRankChangePayload;
import com.meloo.room.api.message.RankUpdatedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;

@Component
public class QueuedRoomRankPublisher implements RoomRankPublisher {
  private final RoomRegistry rooms;
  private final RoomEventPublisher messages;

  public QueuedRoomRankPublisher(RoomRegistry rooms, RoomEventPublisher messages) {
    this.rooms = rooms;
    this.messages = messages;
  }

  @Override
  public CompletableFuture<Void> publish(
      UUID roomId, UUID matchId, List<Change> changes, Instant serverTime) {
    var event =
        new RankUpdatedEvent(
            matchId,
            changes.stream()
                .map(
                    change ->
                        new PlayerRankChangePayload(
                            change.userId(), change.rankDelta(), change.rankPoints()))
                .toList(),
            serverTime);
    // Rooms are ephemeral. Registry replay after a restart must not resurrect a closed room.
    return rooms
        .actor(roomId)
        .map(
            actor ->
                actor.execute(
                    () -> messages.publish(roomId, RoomEventPublisher.RoomTopic.SCOREBOARD, event)))
        .orElseGet(() -> CompletableFuture.completedFuture(null));
  }
}
