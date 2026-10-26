package com.meloo.game.domain;

/** Ném ra khi có thao tác chuyển trạng thái không hợp lệ trong máy trạng thái vòng. */
public class InvalidRoundStateException extends RuntimeException {

  public InvalidRoundStateException(String message) {
    super(message);
  }
}
