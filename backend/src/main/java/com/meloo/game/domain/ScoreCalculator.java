package com.meloo.game.domain;

/** BR-02, BR-07, BR-08: Công thức tính điểm một vòng. */
public final class ScoreCalculator {

  private ScoreCalculator() {}

  /**
   * BR-07: Thưởng tốc độ = max(0, ⌊200 × số mili giây còn lại ÷ 45.000⌋). Chỉ áp dụng khi vòng có
   * giới hạn giờ và lượt đoán đến trước mốc cutoff (45.000 ms). Lượt đoán nhận từ mốc cutoff trở đi
   * bị từ chối trước khi gọi hàm này.
   *
   * @param elapsedMs số mili giây đã trôi qua kể từ khi server bắt đầu vòng
   * @param timeLimitMs thời gian giới hạn của vòng (mặc định 45.000 ms)
   * @param maxBonus thưởng tối đa (mặc định 200)
   * @return số điểm thưởng tốc độ (không âm)
   */
  public static int calculateSpeedBonus(long elapsedMs, long timeLimitMs, int maxBonus) {
    if (elapsedMs < 0) {
      throw new IllegalArgumentException("elapsedMs must be >= 0");
    }
    if (timeLimitMs <= 0) {
      throw new IllegalArgumentException("timeLimitMs must be > 0");
    }
    if (maxBonus < 0) {
      throw new IllegalArgumentException("maxBonus must be >= 0");
    }

    long remainingMs = timeLimitMs - elapsedMs;
    if (remainingMs <= 0) {
      return 0;
    }

    // ⌊maxBonus × remainingMs ÷ timeLimitMs⌋
    long bonus = (maxBonus * remainingMs) / timeLimitMs;
    return (int) Math.max(0, bonus);
  }

  /**
   * BR-08: Điểm vòng = Điểm cơ bản × (1 + hệ số combo) + Thưởng tốc độ. Hệ số combo chỉ nhân điểm
   * cơ bản.
   */
  public static int calculateRoundScore(int baseScore, int comboPercent, int speedBonus) {
    if (baseScore < 0) {
      throw new IllegalArgumentException("baseScore must be >= 0");
    }
    if (comboPercent < 0) {
      throw new IllegalArgumentException("comboPercent must be >= 0");
    }
    if (speedBonus < 0) {
      throw new IllegalArgumentException("speedBonus must be >= 0");
    }

    int comboScore = (baseScore * (100 + comboPercent)) / 100;
    return comboScore + speedBonus;
  }
}
