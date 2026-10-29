package com.meloo.room.api.message;

import java.util.UUID;

/** Score summary of a player during a match. */
public record PlayerScoreSummaryPayload(
    UUID userId, String nickname, int totalScore, int comboStreak, int rank) {}
