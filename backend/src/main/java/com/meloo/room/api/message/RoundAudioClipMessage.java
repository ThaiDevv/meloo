package com.meloo.room.api.message;

import java.time.Instant;

/** Private delivery of player's initial or replaced audio clip token for a round (ADR-008). */
public record RoundAudioClipMessage(
    String type, int roundNo, int level, AudioClipPayload audioClip, Instant serverTime)
    implements RoomMessage {

  public RoundAudioClipMessage(
      int roundNo, int level, AudioClipPayload audioClip, Instant serverTime) {
    this("ROUND_AUDIO_CLIP", roundNo, level, audioClip, serverTime);
  }
}
