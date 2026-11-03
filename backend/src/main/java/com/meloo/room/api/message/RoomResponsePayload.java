package com.meloo.room.api.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Complete room state snapshot. */
public record RoomResponsePayload(
    UUID roomId,
    @Nullable String code,
    String roomType,
    @Nullable UUID hostId,
    String status,
    int roundCount,
    int maxPlayers,
    int currentRound,
    int playerCount,
    List<RoomPlayerSummaryPayload> players,
    @Nullable UUID playlistId,
    @Nullable RoomFilterPayload filter,
    Instant createdAt) {

  public RoomResponsePayload(
      UUID roomId,
      @Nullable String code,
      String roomType,
      @Nullable UUID hostId,
      String status,
      int roundCount,
      int maxPlayers,
      int currentRound,
      int playerCount,
      List<RoomPlayerSummaryPayload> players,
      @Nullable UUID playlistId,
      Instant createdAt) {
    this(
        roomId,
        code,
        roomType,
        hostId,
        status,
        roundCount,
        maxPlayers,
        currentRound,
        playerCount,
        players,
        playlistId,
        null,
        createdAt);
  }
}
