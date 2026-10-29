package com.meloo.room.api.message;

/** Detailed score composition according to BR-02, BR-07, BR-08. */
public record RoundScoreDetailPayload(
    int baseScore, int comboPercent, int speedBonus, int roundScore) {}
