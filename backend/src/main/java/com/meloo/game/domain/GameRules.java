package com.meloo.game.domain;

import java.util.List;

/**
 * BR-01, BR-02, BR-07: Configurable rules for game rounds. Default levels: 2s (1000 pts), 5s (800
 * pts), 10s (600 pts), 20s (400 pts), 30s (200 pts). Default time limit: 45,000 ms.
 */
public record GameRules(
    List<ListeningLevel> levels, long roundTimeLimitMs, long timeoutCutoffMs, int maxSpeedBonus) {

  public static final List<ListeningLevel> DEFAULT_LEVELS =
      List.of(
          new ListeningLevel(1, 2, 1000),
          new ListeningLevel(2, 5, 800),
          new ListeningLevel(3, 10, 600),
          new ListeningLevel(4, 20, 400),
          new ListeningLevel(5, 30, 200));

  public static final long DEFAULT_ROUND_TIME_LIMIT_MS = 45_000L;
  public static final int DEFAULT_MAX_SPEED_BONUS = 200;

  public GameRules {
    if (levels == null || levels.isEmpty()) {
      throw new IllegalArgumentException("levels must not be null or empty");
    }
    levels = List.copyOf(levels);
    if (roundTimeLimitMs <= 0) {
      throw new IllegalArgumentException("roundTimeLimitMs must be > 0");
    }
    if (timeoutCutoffMs <= 0) {
      throw new IllegalArgumentException("timeoutCutoffMs must be > 0");
    }
    if (maxSpeedBonus < 0) {
      throw new IllegalArgumentException("maxSpeedBonus must be >= 0");
    }
  }

  public static GameRules defaultRules() {
    return new GameRules(
        DEFAULT_LEVELS,
        DEFAULT_ROUND_TIME_LIMIT_MS,
        DEFAULT_ROUND_TIME_LIMIT_MS,
        DEFAULT_MAX_SPEED_BONUS);
  }

  public int maxLevel() {
    return levels.size();
  }

  public ListeningLevel getLevel(int index) {
    if (index < 1 || index > levels.size()) {
      throw new IllegalArgumentException(
          "Invalid level index: " + index + ", valid range: 1.." + levels.size());
    }
    return levels.get(index - 1);
  }
}
