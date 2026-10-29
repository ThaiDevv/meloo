package com.meloo.room.api.message;

import java.time.Instant;

/** Private response with new audio clip for player who requested listen-more. */
public record ListenMoreResponseMessage(
    String type, int roundNo, int level, AudioClipPayload audioClip, Instant serverTime)
    implements RoomMessage {

  public ListenMoreResponseMessage(
      int roundNo, int level, AudioClipPayload audioClip, Instant serverTime) {
    this("LISTEN_MORE_RESPONSE", roundNo, level, audioClip, serverTime);
  }
}
