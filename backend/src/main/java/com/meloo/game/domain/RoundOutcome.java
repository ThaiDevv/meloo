package com.meloo.game.domain;

/**
 * Kết quả vòng theo hợp đồng và sự kiện kiến trúc: - CORRECT: đoán đúng - WRONG_OUT: đoán sai ở mức
 * cuối (30s) - GAVE_UP: bỏ qua ở mức 30s - TIMEOUT: hết giờ - MISSED: không tham gia / mất kết nối
 */
public enum RoundOutcome {
  CORRECT,
  WRONG_OUT,
  GAVE_UP,
  TIMEOUT,
  MISSED
}
