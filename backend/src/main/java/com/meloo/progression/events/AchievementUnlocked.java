package com.meloo.progression.events;

import java.time.Instant;
import java.util.UUID;

/** Emitted only when a new badge is persisted, in the same transaction. */
public record AchievementUnlocked(UUID eventId, UUID userId, String code, Instant unlockedAt) {}
