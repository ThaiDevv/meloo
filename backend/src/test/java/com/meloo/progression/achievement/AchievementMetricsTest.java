package com.meloo.progression.achievement;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.game.domain.RoundOutcome;
import com.meloo.game.events.RoundPlayerResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AchievementMetricsTest {
  @Test
  void comboUsesSortedConsecutiveFastCorrectRoundsAndResetsOnGapSlowOrFailure() {
    assertThat(AchievementMetrics.combo(List.of(round(3, 2), round(1, 1), round(2, 2))))
        .isEqualTo(3);
    assertThat(
            AchievementMetrics.combo(List.of(round(1, 1), round(3, 1), round(4, 3), round(5, 1))))
        .isEqualTo(1);
    assertThat(
            AchievementMetrics.combo(
                List.of(
                    round(1, 1),
                    new RoundPlayerResult(2, RoundOutcome.TIMEOUT, null, 0, 0, 0, 0, 0, null),
                    round(3, 1))))
        .isEqualTo(1);
  }

  @Test
  void historicalStreaksSurviveLaterLossAndArrivalOrderWhileMissingDailyDatesBreakRuns() {
    var facts = new ArrayList<AchievementMetrics.Fact>();
    for (int i = 0; i < 5; i++) facts.add(match(i, true, true));
    facts.add(match(5, false, false)); // Abandoned matches do not count as completed matches.
    facts.add(match(6, true, false));
    facts.add(daily(1, true));
    facts.add(daily(2, true));
    facts.add(daily(4, true));
    facts.add(daily(5, true));
    facts.add(daily(6, false));
    facts.add(daily(7, true));
    java.util.Collections.reverse(facts);
    assertThat(AchievementMetrics.calculate(facts))
        .containsEntry("MATCHES", 6L)
        .containsEntry("WINS", 5L)
        .containsEntry("WIN_STREAK", 5L)
        .containsEntry("DAILY_STREAK", 2L)
        .containsEntry("CORRECT", 14L)
        .containsEntry("FAST_CORRECT", 7L)
        .containsEntry("BEST_COMBO", 2L);
    assertThat(AchievementMetrics.calculate(List.of()))
        .allSatisfy((key, value) -> assertThat(value).isZero());
  }

  static RoundPlayerResult round(int index, int level) {
    return new RoundPlayerResult(index, RoundOutcome.CORRECT, level, 100, 0, 0, 100, 0, 100L);
  }

  private AchievementMetrics.Fact match(int seconds, boolean completed, boolean won) {
    return new AchievementMetrics.Fact(
        "MATCH", completed, won, 2, 1, 2, null, Instant.EPOCH.plusSeconds(seconds));
  }

  private AchievementMetrics.Fact daily(int day, boolean won) {
    return new AchievementMetrics.Fact(
        "DAILY", true, won, 0, 0, 0, LocalDate.of(2026, 1, day), Instant.EPOCH);
  }
}
