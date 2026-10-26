package com.meloo.game.events;

import com.meloo.game.domain.RoundOutcome;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Kết quả thi đấu của một người chơi ở một vòng theo mục 2 tài liệu 04-su-kien-va-api-module.md.
 */
public record RoundPlayerResult(
    int roundNo,
    RoundOutcome outcome,
    @Nullable Integer correctLevel,
    int baseScore,
    int comboPercent,
    int speedBonus,
    int roundScore,
    int wrongCount,
    @Nullable Long answerTimeMs) {

  public RoundPlayerResult {
    if (roundNo < 1) {
      throw new IllegalArgumentException("roundNo must be >= 1");
    }
    Objects.requireNonNull(outcome, "outcome must not be null");
    if (baseScore < 0) {
      throw new IllegalArgumentException("baseScore must be >= 0");
    }
    if (comboPercent < 0) {
      throw new IllegalArgumentException("comboPercent must be >= 0");
    }
    if (speedBonus < 0) {
      throw new IllegalArgumentException("speedBonus must be >= 0");
    }
    if (roundScore < 0) {
      throw new IllegalArgumentException("roundScore must be >= 0");
    }
    if (wrongCount < 0) {
      throw new IllegalArgumentException("wrongCount must be >= 0");
    }
  }
}
