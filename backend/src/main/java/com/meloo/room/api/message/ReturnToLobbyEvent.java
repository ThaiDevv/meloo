package com.meloo.room.api.message;

import java.time.Instant;

/** Broadcast when room returns to lobby after match completion (BR-12). */
public record ReturnToLobbyEvent(String type, Instant serverTime) implements RoomMessage {

  public ReturnToLobbyEvent(Instant serverTime) {
    this("RETURN_TO_LOBBY", serverTime);
  }
}
