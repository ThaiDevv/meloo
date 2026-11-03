package com.meloo.room.api.message;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Command to update room settings (host only, BR-12). */
public record SettingsCommand(
    @Nullable Integer roundCount,
    @Nullable Integer maxPlayers,
    @Nullable UUID playlistId,
    @Nullable RoomFilterPayload filter) {

  public SettingsCommand(
      @Nullable Integer roundCount, @Nullable Integer maxPlayers, @Nullable UUID playlistId) {
    this(roundCount, maxPlayers, playlistId, null);
  }
}
