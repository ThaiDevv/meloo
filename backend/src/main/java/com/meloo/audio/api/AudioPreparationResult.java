package com.meloo.audio.api;

import org.jspecify.annotations.Nullable;

/** Result of preparing audio for a round (AC 6, BR-27). */
public record AudioPreparationResult(boolean playable, @Nullable String failureReason) {

  public static AudioPreparationResult success() {
    return new AudioPreparationResult(true, null);
  }

  public static AudioPreparationResult failure(String reason) {
    return new AudioPreparationResult(false, reason);
  }
}
