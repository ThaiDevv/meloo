package com.meloo.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScoreCalculatorTest {

  @ParameterizedTest(name = "Mức {0} ({1}s) -> Điểm cơ bản {2}")
  @CsvSource({"1, 2, 1000", "2, 5, 800", "3, 10, 600", "4, 20, 400", "5, 30, 200"})
  @DisplayName("BR-01, BR-02: 5 mức nghe và điểm cơ bản chuẩn")
  void testBaseScoresAndConfigurableLevels(int levelIndex, int durationSec, int expectedBaseScore) {
    GameRules rules = GameRules.defaultRules();
    ListeningLevel level = rules.getLevel(levelIndex);

    assertThat(level.levelIndex()).isEqualTo(levelIndex);
    assertThat(level.durationSeconds()).isEqualTo(durationSec);
    assertThat(level.baseScore()).isEqualTo(expectedBaseScore);
  }

  @ParameterizedTest(name = "Còn {0} ms (elapsed {1} ms) -> Thưởng {2}")
  @CsvSource({
    "30000, 15000, 133", // Ví dụ mục 3.2: ⌊200 * 30.000 / 45.000⌋ = 133
    "44999, 1, 199", // Còn 44.999 ms (mới 1 ms) -> thưởng 199
    "1, 44999, 0", // Còn 1 ms (sát giờ) -> được chấm, thưởng 0
    "0, 45000, 0", // Đúng mốc 45.000 ms -> thưởng 0 (nhưng ngoài engine sẽ bị từ chối)
    "45000, 0, 200", // Còn nguyên 45.000 ms (0 ms) -> thưởng 200 tối đa
    "22500, 22500, 100" // Đúng nửa thời gian -> thưởng 100
  })
  @DisplayName("BR-07: Công thức thưởng tốc độ max(0, ⌊200 × số mili giây còn lại ÷ 45.000⌋)")
  void testSpeedBonusFormula(long remainingMs, long elapsedMs, int expectedBonus) {
    long timeLimitMs = 45_000L;
    int maxBonus = 200;
    int bonus = ScoreCalculator.calculateSpeedBonus(elapsedMs, timeLimitMs, maxBonus);
    assertThat(bonus).isEqualTo(expectedBonus);
  }

  @ParameterizedTest(name = "Cơ bản {0}, Combo {1}%, Tốc độ {2} -> Tổng {3}")
  @CsvSource({
    "800, 10, 133, 1013", // Ví dụ mục 3.2: đúng ở 5s, vòng thứ 2 của combo, còn 30.000 ms -> 1.013
    // điểm
    "1000, 0, 199, 1199", // Mức 2s, không combo, còn 44.999 ms
    "1000, 20, 200, 1400", // Mức 2s, combo >= 3 (+20% = 1200), max tốc độ (+200) -> 1400 điểm
    "800, 20, 133, 1093", // Mức 5s, combo >= 3 (+20% = 960), thưởng 133 -> 1093 điểm
    "600, 0, 0, 600", // Mức 10s (không combo), thưởng tốc độ 0
    "200, 0, 0, 200", // Mức 30s, thưởng 0
    "0, 0, 0, 0" // 0 điểm
  })
  @DisplayName("BR-02, BR-07, BR-08: Công thức điểm vòng tổng hợp")
  void testRoundScoreCalculation(
      int baseScore, int comboPercent, int speedBonus, int expectedTotal) {
    int total = ScoreCalculator.calculateRoundScore(baseScore, comboPercent, speedBonus);
    assertThat(total).isEqualTo(expectedTotal);
  }

  @Test
  @DisplayName("Thưởng tốc độ và điểm vòng không bao giờ ra số âm")
  void testNoNegativeScores() {
    // Quá 45s (ví dụ 46.000 ms)
    assertThat(ScoreCalculator.calculateSpeedBonus(46_000L, 45_000L, 200)).isZero();
    assertThat(ScoreCalculator.calculateSpeedBonus(100_000L, 45_000L, 200)).isZero();

    // Điểm với bonus 0
    assertThat(ScoreCalculator.calculateRoundScore(0, 0, 0)).isZero();
  }

  @Test
  @DisplayName("Tham số không hợp lệ ném IllegalArgumentException")
  void testInvalidParametersThrow() {
    assertThatThrownBy(() -> ScoreCalculator.calculateSpeedBonus(-1, 45000, 200))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ScoreCalculator.calculateSpeedBonus(100, 0, 200))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ScoreCalculator.calculateSpeedBonus(100, 45000, -1))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> ScoreCalculator.calculateRoundScore(-1, 10, 100))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ScoreCalculator.calculateRoundScore(800, -1, 100))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ScoreCalculator.calculateRoundScore(800, 10, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
