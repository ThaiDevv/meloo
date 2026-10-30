package com.meloo.room.api;

import java.util.Optional;
import java.util.UUID;

/** Read-only room lookup for modules that need to enforce room-scoped access. */
public interface RoomDirectory {

  Optional<RoomDescriptor> find(UUID roomId);

  default boolean isMember(UUID roomId, UUID userId) {
    return find(roomId).map(room -> room.hasMember(userId)).orElse(false);
  }
}
