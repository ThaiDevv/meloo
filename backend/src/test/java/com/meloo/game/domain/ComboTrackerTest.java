package com.meloo.game.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComboTrackerTest {

  @Test
  @DisplayName("BR-08: Chuỗi combo tăng và áp dụng đúng 0%, 10%, 20%")
  void testComboStreakProgression() {
    ComboTracker tracker = new ComboTracker(true);

    // Vòng 1: đúng ở mức 2s (level 1) -> streak = 1, combo = 0%
    int combo1 = tracker.recordRound(RoundOutcome.CORRECT, 1);
    assertThat(tracker.currentStreak()).isEqualTo(1);
    assertThat(combo1).isZero();

    // Vòng 2: đúng ở mức 5s (level 2) -> streak = 2, combo = 10%
    int combo2 = tracker.recordRound(RoundOutcome.CORRECT, 2);
    assertThat(tracker.currentStreak()).isEqualTo(2);
    assertThat(combo2).isEqualTo(10);

    // Vòng 3: đúng ở mức 2s (level 1) -> streak = 3, combo = 20%
    int combo3 = tracker.recordRound(RoundOutcome.CORRECT, 1);
    assertThat(tracker.currentStreak()).isEqualTo(3);
    assertThat(combo3).isEqualTo(20);

    // Vòng 4: đúng ở mức 5s (level 2) -> streak = 4, combo vẫn 20%
    int combo4 = tracker.recordRound(RoundOutcome.CORRECT, 2);
    assertThat(tracker.currentStreak()).isEqualTo(4);
    assertThat(combo4).isEqualTo(20);

    // Vòng 5: đúng ở mức 10s (level 3) -> đứt chuỗi, streak = 0, combo = 0%
    int combo5 = tracker.recordRound(RoundOutcome.CORRECT, 3);
    assertThat(tracker.currentStreak()).isZero();
    assertThat(combo5).isZero();

    // Vòng 6: đúng ở mức 2s -> bắt đầu chuỗi mới, streak = 1, combo = 0%
    int combo6 = tracker.recordRound(RoundOutcome.CORRECT, 1);
    assertThat(tracker.currentStreak()).isEqualTo(1);
    assertThat(combo6).isZero();
  }

  @Test
  @DisplayName("BR-08: Đứt chuỗi khi đoán sai, bỏ qua, hoặc hết giờ")
  void testComboBreaksOnNonSuccess() {
    ComboTracker tracker = new ComboTracker(true);
    tracker.recordRound(RoundOutcome.CORRECT, 1);
    tracker.recordRound(RoundOutcome.CORRECT, 2);
    assertThat(tracker.currentStreak()).isEqualTo(2);

    // Vòng này đoán sai hết lượt
    int combo = tracker.recordRound(RoundOutcome.WRONG_OUT, 0);
    assertThat(tracker.currentStreak()).isZero();
    assertThat(combo).isZero();

    // Bắt đầu lại
    tracker.recordRound(RoundOutcome.CORRECT, 1);
    assertThat(tracker.currentStreak()).isEqualTo(1);

    // Bỏ qua (GAVE_UP)
    tracker.recordRound(RoundOutcome.GAVE_UP, 0);
    assertThat(tracker.currentStreak()).isZero();

    // Bắt đầu lại
    tracker.recordRound(RoundOutcome.CORRECT, 1);
    assertThat(tracker.currentStreak()).isEqualTo(1);

    // Hết giờ (TIMEOUT)
    tracker.recordRound(RoundOutcome.TIMEOUT, 0);
    assertThat(tracker.currentStreak()).isZero();
  }

  @Test
  @DisplayName("BR-08: Thử thách ngày không áp dụng combo (comboEnabled = false)")
  void testComboDisabledForDailyChallenge() {
    ComboTracker tracker = new ComboTracker(false);
    assertThat(tracker.isComboEnabled()).isFalse();

    int combo1 = tracker.recordRound(RoundOutcome.CORRECT, 1);
    assertThat(tracker.currentStreak()).isZero();
    assertThat(combo1).isZero();

    int combo2 = tracker.recordRound(RoundOutcome.CORRECT, 1);
    assertThat(tracker.currentStreak()).isZero();
    assertThat(combo2).isZero();
  }

  @Test
  @DisplayName("Reset xóa chuỗi combo")
  void testReset() {
    ComboTracker tracker = new ComboTracker(true);
    tracker.recordRound(RoundOutcome.CORRECT, 1);
    tracker.recordRound(RoundOutcome.CORRECT, 2);
    assertThat(tracker.currentStreak()).isEqualTo(2);

    tracker.reset();
    assertThat(tracker.currentStreak()).isZero();
  }
}
