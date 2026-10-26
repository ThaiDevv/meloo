package com.meloo.game.domain;

/** Ném ra khi lượt đoán bị từ chối (vd: server nhận từ mốc 45.000 ms trở đi - BR-07). */
public class GuessRejectedException extends RuntimeException {

  public GuessRejectedException(String message) {
    super(message);
  }
}
