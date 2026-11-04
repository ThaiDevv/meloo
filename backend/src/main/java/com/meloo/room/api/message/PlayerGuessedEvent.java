package com.meloo.room.api.message;

import java.time.Instant;
import java.util.UUID;

/**
 * Broadcast that a player guessed correctly with level, round score and total score, NO answer
 * leaked (BR-07).
 */
public record PlayerGuessedEvent(
    String type,
    UUID userId,
    String nickname,
    int level,
    boolean isCorrect,
    int roundScore,
    int totalScore,
    Instant serverTime)
    implements RoomMessage {

  public PlayerGuessedEvent(
      UUID userId,
      String nickname,
      int level,
      boolean isCorrect,
      int roundScore,
      int totalScore,
      Instant serverTime) {
    this("PLAYER_GUESSED", userId, nickname, level, isCorrect, roundScore, totalScore, serverTime);
  }

  public PlayerGuessedEvent(
      UUID userId, String nickname, int level, int roundScore, int totalScore, Instant serverTime) {
    this("PLAYER_GUESSED", userId, nickname, level, true, roundScore, totalScore, serverTime);
  }

  /** Backwards compatible constructor defaulting scores to 0. */
  public PlayerGuessedEvent(
      UUID userId, String nickname, int level, boolean isCorrect, Instant serverTime) {
    this("PLAYER_GUESSED", userId, nickname, level, isCorrect, 0, 0, serverTime);
  }

  /** Backwards compatible constructor defaulting scores to 0 and isCorrect to true. */
  public PlayerGuessedEvent(UUID userId, String nickname, int level, Instant serverTime) {
    this("PLAYER_GUESSED", userId, nickname, level, true, 0, 0, serverTime);
  }
}
