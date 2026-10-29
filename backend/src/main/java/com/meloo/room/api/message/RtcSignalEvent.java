package com.meloo.room.api.message;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** WebRTC signaling placeholder for voice in S5. */
public record RtcSignalEvent(
    String type,
    UUID senderId,
    @Nullable UUID targetUserId,
    String signalType,
    String payload,
    Instant serverTime)
    implements RoomMessage {

  public RtcSignalEvent(
      UUID senderId,
      @Nullable UUID targetUserId,
      String signalType,
      String payload,
      Instant serverTime) {
    this("RTC_SIGNAL", senderId, targetUserId, signalType, payload, serverTime);
  }
}
