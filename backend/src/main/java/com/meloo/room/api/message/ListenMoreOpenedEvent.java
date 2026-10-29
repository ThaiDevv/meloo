package com.meloo.room.api.message;

import java.time.Instant;
import java.util.UUID;

/** A player unlocked the next listening level. */
public record ListenMoreOpenedEvent(
    String type, UUID userId, int level, AudioClipPayload audioClip, Instant serverTime)
    implements RoomMessage {

  public ListenMoreOpenedEvent(
      UUID userId, int level, AudioClipPayload audioClip, Instant serverTime) {
    this("LISTEN_MORE_OPENED", userId, level, audioClip, serverTime);
  }
}
