package com.meloo.room.api.message;

import java.util.UUID;

/** Final match standings with millisecond tie-break (BR-10). */
public record PlayerRankingPayload(
    int rank,
    UUID userId,
    String nickname,
    int totalScore,
    int correctCount,
    long totalCorrectTimeMs) {}
