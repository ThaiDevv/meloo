package com.meloo.room.api.message;

import java.time.Instant;
import java.util.UUID;

/** Broadcast that a player guessed correctly with level, NO answer leaked (BR-07). */
public record PlayerGuessedEvent(
    String type, UUID userId, String nickname, int level, boolean isCorrect, Instant serverTime)
    implements RoomMessage {

  public PlayerGuessedEvent(UUID userId, String nickname, int level, Instant serverTime) {
    this("PLAYER_GUESSED", userId, nickname, level, true, serverTime);
  }

  public PlayerGuessedEvent(
      UUID userId, String nickname, int level, boolean isCorrect, Instant serverTime) {
    this("PLAYER_GUESSED", userId, nickname, level, isCorrect, serverTime);
  }
}
