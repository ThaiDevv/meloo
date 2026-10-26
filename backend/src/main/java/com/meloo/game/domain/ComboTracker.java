package com.meloo.game.domain;

/**
 * BR-08: Quản lý chuỗi combo. - Áp dụng cho chơi đơn và nhiều người; không áp dụng cho thử thách
 * ngày. - Đoán đúng ở mức 2s (level 1) hoặc 5s (level 2) thì nối chuỗi. - Vòng có kết quả khác
 * (đoán ở mức > 5s, đoán sai, bỏ qua, hết giờ) thì đứt chuỗi (reset về 0). - Vòng thứ 2 liên tiếp
 * được +10% điểm cơ bản. - Từ vòng thứ 3 liên tiếp trở đi được +20% điểm cơ bản.
 */
public class ComboTracker {

  private final boolean comboEnabled;
  private int currentStreak;
  private int previousStreak;

  public ComboTracker(boolean comboEnabled) {
    this.comboEnabled = comboEnabled;
    this.currentStreak = 0;
    this.previousStreak = 0;
  }

  public boolean isComboEnabled() {
    return comboEnabled;
  }

  public int currentStreak() {
    return currentStreak;
  }

  public int previousStreak() {
    return previousStreak;
  }

  /**
   * Cập nhật chuỗi sau khi một vòng kết thúc và trả về hệ số combo (%) áp dụng cho vòng đó.
   *
   * @param outcome kết quả vòng
   * @param correctLevel mức đoán đúng (1..5), chỉ có ý nghĩa khi outcome == CORRECT
   * @return hệ số combo (0, 10, hoặc 20)
   */
  public int recordRound(RoundOutcome outcome, int correctLevel) {
    this.previousStreak = this.currentStreak;
    if (!comboEnabled) {
      currentStreak = 0;
      return 0;
    }

    if (outcome == RoundOutcome.CORRECT && correctLevel >= 1 && correctLevel <= 2) {
      currentStreak++;
      if (currentStreak == 2) {
        return 10;
      } else if (currentStreak >= 3) {
        return 20;
      } else {
        return 0; // Vòng đầu tiên của chuỗi
      }
    } else {
      currentStreak = 0;
      return 0;
    }
  }

  /**
   * Khôi phục lại streak trước lần gọi recordRound gần nhất. Dùng khi khôi phục trạng thái sau khi
   * timeout() đã chạy nhưng có lượt đoán hợp lệ trước cutoff.
   */
  public void restorePreviousStreak() {
    if (!comboEnabled) {
      this.currentStreak = 0;
      this.previousStreak = 0;
      return;
    }
    this.currentStreak = this.previousStreak;
    this.previousStreak = 0;
  }

  public void reset() {
    this.currentStreak = 0;
    this.previousStreak = 0;
  }
}
