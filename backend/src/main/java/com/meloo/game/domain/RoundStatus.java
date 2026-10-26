package com.meloo.game.domain;

/**
 * Máy trạng thái vòng: CHỜ (WAITING) -> ĐANG_CHƠI (IN_PROGRESS) -> ĐÚNG (CORRECT) / HẾT_LƯỢT
 * (OUT_OF_ATTEMPTS) / HẾT_GIỜ (TIMEOUT).
 */
public enum RoundStatus {
  WAITING,
  IN_PROGRESS,
  CORRECT,
  OUT_OF_ATTEMPTS,
  TIMEOUT;

  public boolean isTerminal() {
    return this == CORRECT || this == OUT_OF_ATTEMPTS || this == TIMEOUT;
  }
}
