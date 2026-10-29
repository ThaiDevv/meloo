package com.meloo.room.api.message;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Private error notification (e.g. blocked chat BR-16, BR-17 or invalid command). */
public record ErrorMessage(
    String type, String code, String message, @Nullable String detail, Instant serverTime)
    implements RoomMessage {

  public ErrorMessage(String code, String message, @Nullable String detail, Instant serverTime) {
    this("ERROR", code, message, detail, serverTime);
  }
}
