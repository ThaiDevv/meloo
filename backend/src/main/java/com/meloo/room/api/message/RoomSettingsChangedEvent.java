package com.meloo.room.api.message;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Broadcast when host modifies room configuration. */
public record RoomSettingsChangedEvent(
    String type,
    int roundCount,
    int maxPlayers,
    @Nullable UUID playlistId,
    @Nullable RoomFilterPayload filter,
    Instant serverTime)
    implements RoomMessage {

  public RoomSettingsChangedEvent(
      int roundCount,
      int maxPlayers,
      @Nullable UUID playlistId,
      @Nullable RoomFilterPayload filter,
      Instant serverTime) {
    this("ROOM_SETTINGS_CHANGED", roundCount, maxPlayers, playlistId, filter, serverTime);
  }

  public RoomSettingsChangedEvent(
      int roundCount, int maxPlayers, @Nullable UUID playlistId, Instant serverTime) {
    this(roundCount, maxPlayers, playlistId, null, serverTime);
  }
}
