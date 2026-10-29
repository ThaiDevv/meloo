package com.meloo.room.api.message;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Private error notification (e.g. blocked chat BR-16, BR-17 or start failure BR-09, UC-17). */
public record ErrorMessage(
    String type,
    String code,
    String message,
    @Nullable String detail,
    @Nullable String startFailureReason,
    @Nullable Integer missingTracks,
    Instant serverTime)
    implements RoomMessage {

  public ErrorMessage(
      String code,
      String message,
      @Nullable String detail,
      @Nullable String startFailureReason,
      @Nullable Integer missingTracks,
      Instant serverTime) {
    this("ERROR", code, message, detail, startFailureReason, missingTracks, serverTime);
  }

  public ErrorMessage(String code, String message, @Nullable String detail, Instant serverTime) {
    this(code, message, detail, null, null, serverTime);
  }
}
