package com.meloo.room.api;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable read model describing the authorization-relevant state of a room. */
public record RoomDescriptor(UUID roomId, Set<UUID> memberIds, boolean privateRoom) {

  public RoomDescriptor {
    Objects.requireNonNull(roomId, "roomId");
    memberIds = Set.copyOf(Objects.requireNonNull(memberIds, "memberIds"));
  }

  public boolean hasMember(UUID userId) {
    return memberIds.contains(userId);
  }
}
