package com.meloo.room.matchmaking;

import java.util.UUID;

/** Internal lifecycle notification, emitted on the room actor after a join or return to lobby. */
public record QuickMatchLobbyChanged(UUID roomId, boolean returnedToLobby) {}
