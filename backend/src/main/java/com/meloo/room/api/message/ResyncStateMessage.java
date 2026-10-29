package com.meloo.room.api.message;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Full room snapshot delivered to a player upon resync or join (BR-14). */
public record ResyncStateMessage(
    String type,
    RoomResponsePayload room,
    String roomPhase,
    @Nullable ActiveRoundStatePayload currentRound,
    List<PlayerScoreSummaryPayload> scores,
    Instant serverTime)
    implements RoomMessage {

  public ResyncStateMessage(
      RoomResponsePayload room,
      String roomPhase,
      @Nullable ActiveRoundStatePayload currentRound,
      List<PlayerScoreSummaryPayload> scores,
      Instant serverTime) {
    this("RESYNC_STATE", room, roomPhase, currentRound, scores, serverTime);
  }
}
