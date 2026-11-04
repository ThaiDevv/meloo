package com.meloo.chat.api;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record ChatErrorMessage(
    String type, String code, String message, @Nullable String detail, Instant serverTime) {

  public ChatErrorMessage(
      String code, String message, @Nullable String detail, Instant serverTime) {
    this("ERROR", code, message, detail, serverTime);
  }
}
