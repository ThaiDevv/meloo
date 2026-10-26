package com.meloo.game.domain;

import org.jspecify.annotations.Nullable;

/** Kết quả sau khi engine đánh giá một lượt đoán. */
public record GuessEvaluation(
    boolean correct,
    boolean rightArtist,
    boolean roundEnded,
    RoundStatus status,
    @Nullable RoundOutcome outcome,
    @Nullable Integer correctLevel,
    int wrongCount,
    RoundScoreDetail scoreDetail) {

  public GuessEvaluation {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    if (scoreDetail == null) {
      throw new IllegalArgumentException("scoreDetail must not be null");
    }
    if (wrongCount < 0) {
      throw new IllegalArgumentException("wrongCount must be >= 0");
    }
  }
}
