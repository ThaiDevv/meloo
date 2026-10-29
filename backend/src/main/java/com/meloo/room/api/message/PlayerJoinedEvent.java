package com.meloo.room.api.message;

import java.time.Instant;

/** Broadcast when a new player joins the room. */
public record PlayerJoinedEvent(String type, RoomPlayerSummaryPayload user, Instant serverTime)
    implements RoomMessage {

  public PlayerJoinedEvent(RoomPlayerSummaryPayload user, Instant serverTime) {
    this("PLAYER_JOINED", user, serverTime);
  }
}
