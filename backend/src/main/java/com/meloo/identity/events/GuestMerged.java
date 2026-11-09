package com.meloo.identity.events;

import java.time.Instant;
import java.util.UUID;

public record GuestMerged(UUID eventId, UUID guestId, UUID userId, Instant occurredAt) {}
