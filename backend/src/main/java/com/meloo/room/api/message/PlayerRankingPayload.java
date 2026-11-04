package com.meloo.room.api.message;

import java.util.UUID;

/**
 * Final match standings with millisecond tie-break (BR-10) and early departure indicator (BR-14).
 */
public record PlayerRankingPayload(
    int rank,
    UUID userId,
    String nickname,
    int totalScore,
    int correctCount,
    long totalCorrectTimeMs,
    boolean left) {

  public PlayerRankingPayload(
      int rank,
      UUID userId,
      String nickname,
      int totalScore,
      int correctCount,
      long totalCorrectTimeMs) {
    this(rank, userId, nickname, totalScore, correctCount, totalCorrectTimeMs, false);
  }
}
