package com.meloo.room.api.message;

import java.util.UUID;

/** Command to kick a player from the room (host only, BR-12). */
public record KickCommand(UUID targetUserId) {}
