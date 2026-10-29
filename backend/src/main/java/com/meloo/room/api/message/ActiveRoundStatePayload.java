package com.meloo.room.api.message;

/** In-progress round state without revealing track metadata. */
public record ActiveRoundStatePayload(
    int roundNo,
    int totalRounds,
    int currentLevel,
    AudioClipPayload audioClip,
    int remainingMs,
    boolean hasGuessed) {}
