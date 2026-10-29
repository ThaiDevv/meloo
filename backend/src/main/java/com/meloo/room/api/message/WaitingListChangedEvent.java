package com.meloo.room.api.message;

import java.time.Instant;
import java.util.List;

/** Broadcast updated waiting list of players in lobby while match is running (BR-15). */
public record WaitingListChangedEvent(
    String type, List<RoomPlayerSummaryPayload> waitingPlayers, Instant serverTime)
    implements RoomMessage {

  public WaitingListChangedEvent(
      List<RoomPlayerSummaryPayload> waitingPlayers, Instant serverTime) {
    this("WAITING_LIST_CHANGED", waitingPlayers, serverTime);
  }
}
