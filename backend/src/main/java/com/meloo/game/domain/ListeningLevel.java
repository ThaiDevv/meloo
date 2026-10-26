package com.meloo.game.domain;

/**
 * BR-01, BR-02: Listening level configuration. Contains level index (1-based), duration in seconds,
 * and base score.
 */
public record ListeningLevel(int levelIndex, int durationSeconds, int baseScore) {

  public ListeningLevel {
    if (levelIndex < 1) {
      throw new IllegalArgumentException("levelIndex must be >= 1");
    }
    if (durationSeconds <= 0) {
      throw new IllegalArgumentException("durationSeconds must be > 0");
    }
    if (baseScore < 0) {
      throw new IllegalArgumentException("baseScore must be >= 0");
    }
  }
}
