package com.meloo.game.events;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Thống kê và kết quả của một người chơi trong trận theo mục 2 tài liệu
 * 04-su-kien-va-api-module.md.
 */
public record PlayerSummary(
    @Nullable UUID userId,
    String nickname,
    boolean isGuest,
    int totalScore,
    int rank,
    int correctCount,
    long correctTimeMs,
    List<RoundPlayerResult> results) {

  public PlayerSummary {
    Objects.requireNonNull(nickname, "nickname must not be null");
    if (totalScore < 0) {
      throw new IllegalArgumentException("totalScore must be >= 0");
    }
    if (rank < 1) {
      throw new IllegalArgumentException("rank must be >= 1");
    }
    if (correctCount < 0) {
      throw new IllegalArgumentException("correctCount must be >= 0");
    }
    if (correctTimeMs < 0) {
      throw new IllegalArgumentException("correctTimeMs must be >= 0");
    }
    results = results == null ? List.of() : List.copyOf(results);
  }
}
