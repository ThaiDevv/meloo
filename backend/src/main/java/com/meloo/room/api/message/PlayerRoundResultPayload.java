package com.meloo.room.api.message;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Round performance for one player with detailed score breakdown. */
public record PlayerRoundResultPayload(
    UUID userId,
    String outcome,
    @Nullable Integer correctLevel,
    int baseScore,
    int comboPercent,
    int speedBonus,
    int roundScore,
    int wrongCount,
    @Nullable Long answerTimeMs) {}
