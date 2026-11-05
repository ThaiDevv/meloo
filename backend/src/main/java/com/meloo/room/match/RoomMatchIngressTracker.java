package com.meloo.room.match;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks gameplay frames received by STOMP but not yet dispatched to their room actor. */
final class RoomMatchIngressTracker {
  private final Map<UUID, Entry> pending = new HashMap<>();

  synchronized Receipt register(UUID roomId, Clock clock) {
    Instant receivedAt = clock.instant();
    UUID token = UUID.randomUUID();
    pending.put(token, new Entry(roomId, receivedAt));
    return new Receipt(token, receivedAt);
  }

  synchronized void dispatched(UUID token) {
    pending.remove(token);
  }

  synchronized boolean hasPendingBefore(UUID roomId, Instant deadline) {
    return pending.values().stream()
        .anyMatch(entry -> entry.roomId().equals(roomId) && entry.receivedAt().isBefore(deadline));
  }

  record Receipt(UUID token, Instant receivedAt) {}

  private record Entry(UUID roomId, Instant receivedAt) {}
}
