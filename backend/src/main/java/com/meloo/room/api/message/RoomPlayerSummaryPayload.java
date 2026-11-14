package com.meloo.room.api.message;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Player summary in room lobby and game. */
public record RoomPlayerSummaryPayload(
    UUID userId,
    String nickname,
    @Nullable String avatarUrl,
    @Nullable String avatarId,
    boolean isHost,
    boolean isReady,
    boolean isGuest,
    boolean isConnected,
    int score) {}
