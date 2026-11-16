package com.meloo.progression.achievement;

import com.meloo.game.domain.RoundOutcome;
import com.meloo.game.events.RoundPlayerResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class AchievementMetrics {
  private AchievementMetrics() {}

  record Fact(
      String kind,
      boolean completed,
      boolean won,
      int correct,
      int fastCorrect,
      int bestCombo,
      @Nullable LocalDate date,
      Instant endedAt) {}

  static int combo(List<RoundPlayerResult> rounds) {
    int current = 0, best = 0, previous = 0;
    for (var round :
        rounds.stream().sorted(Comparator.comparingInt(RoundPlayerResult::roundNo)).toList()) {
      boolean fast =
          round.outcome() == RoundOutcome.CORRECT
              && round.correctLevel() != null
              && round.correctLevel() <= 2;
      current = fast ? (round.roundNo() == previous + 1 ? current + 1 : 1) : 0;
      best = Math.max(best, current);
      previous = round.roundNo();
    }
    return best;
  }

  static Map<String, Long> calculate(List<Fact> facts) {
    long matches = 0, wins = 0, correct = 0, fast = 0, combo = 0;
    long winRun = 0, bestWins = 0, dailyRun = 0, bestDaily = 0;
    LocalDate previous = null;
    // The query supplies a stable source-id order for simultaneous finishes.
    for (var fact :
        facts.stream()
            .filter(f -> f.kind().equals("MATCH"))
            .sorted(Comparator.comparing(Fact::endedAt))
            .toList()) {
      correct += fact.correct();
      fast += fact.fastCorrect();
      combo = Math.max(combo, fact.bestCombo());
      if (fact.completed()) {
        matches++;
        if (fact.won()) wins++;
        winRun = fact.won() ? winRun + 1 : 0;
        bestWins = Math.max(bestWins, winRun);
      }
    }
    for (var fact :
        facts.stream()
            .filter(f -> f.kind().equals("DAILY"))
            .sorted(Comparator.comparing(f -> java.util.Objects.requireNonNull(f.date())))
            .toList()) {
      LocalDate date = java.util.Objects.requireNonNull(fact.date());
      dailyRun =
          fact.won()
              ? (previous != null && date.equals(previous.plusDays(1)) ? dailyRun + 1 : 1)
              : 0;
      bestDaily = Math.max(bestDaily, dailyRun);
      previous = date;
    }
    return Map.of(
        "MATCHES",
        matches,
        "WINS",
        wins,
        "CORRECT",
        correct,
        "FAST_CORRECT",
        fast,
        "BEST_COMBO",
        combo,
        "WIN_STREAK",
        bestWins,
        "DAILY_STREAK",
        bestDaily);
  }
}
