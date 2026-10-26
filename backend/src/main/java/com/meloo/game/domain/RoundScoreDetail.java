package com.meloo.game.domain;

/**
 * Chi tiết điểm vòng theo BR-02, BR-07, BR-08: - baseScore: điểm cơ bản theo mức nghe (1.000, 800,
 * 600, 400, 200 hoặc 0) - comboPercent: hệ số combo (0, 10, 20) - speedBonus: thưởng tốc độ (max(0,
 * ⌊200 × ms / 45.000⌋)) - roundScore: baseScore * (1 + comboPercent / 100) + speedBonus
 */
public record RoundScoreDetail(int baseScore, int comboPercent, int speedBonus, int roundScore) {

  public RoundScoreDetail {
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
  }

  public static RoundScoreDetail zero() {
    return new RoundScoreDetail(0, 0, 0, 0);
  }
}
