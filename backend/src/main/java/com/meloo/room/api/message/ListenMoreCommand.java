package com.meloo.room.api.message;

/** Command to unlock next listening duration level (2s -> 5s -> 10s -> 20s -> 30s, BR-04). */
public record ListenMoreCommand(int roundNo, int currentLevel) {}
