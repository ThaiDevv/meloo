package com.meloo.identity.events;

import java.time.Instant;
import java.util.UUID;

public record GuestCreated(UUID eventId, UUID userId, String nickname, Instant createdAt) {

  public GuestCreated {
    if (eventId == null) {
      throw new IllegalArgumentException("eventId must not be null");
    }
    if (userId == null) {
      throw new IllegalArgumentException("userId must not be null");
    }
    if (nickname == null || nickname.isBlank()) {
      throw new IllegalArgumentException("nickname must not be blank");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
  }
}
