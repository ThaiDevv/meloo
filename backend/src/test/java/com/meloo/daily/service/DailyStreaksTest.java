package com.meloo.daily.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.daily.persistence.AttemptStatus;
import com.meloo.daily.persistence.DailyAttempt;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyStreaksTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

  @Test
  void preservesYesterdayStreakUntilTodayFailsAndClipsLongestToQueryWindow() {
    var history = List.of(solved(-4), solved(-3), solved(-2), solved(-1));
    assertThat(DailyStreaks.current(history, TODAY)).isEqualTo(4);
    assertThat(DailyStreaks.longest(history, TODAY.minusDays(2), TODAY)).isEqualTo(2);
    assertThat(
            DailyStreaks.current(
                List.of(
                    solved(-1),
                    DailyAttempt.builder()
                        .challengeDate(TODAY)
                        .status(AttemptStatus.UNSOLVED)
                        .build()),
                TODAY))
        .isZero();
    assertThat(DailyStreaks.current(List.of(solved(-2)), TODAY)).isZero();
    assertThat(
            DailyStreaks.longest(
                List.of(solved(-5), solved(-3), solved(-2)), LocalDate.MIN, LocalDate.MAX))
        .isEqualTo(2);
    assertThat(DailyStreaks.current(List.of(solved(-1), solved(0)), TODAY)).isEqualTo(2);
    assertThat(DailyStreaks.longest(List.of(), LocalDate.MIN, LocalDate.MAX)).isZero();
  }

  private DailyAttempt solved(int days) {
    return DailyAttempt.builder()
        .challengeDate(TODAY.plusDays(days))
        .status(AttemptStatus.SOLVED)
        .build();
  }
}
