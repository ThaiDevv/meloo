package com.meloo.identity.events;

import java.time.Instant;
import java.util.UUID;

public record UserRegistered(UUID eventId, UUID userId, Instant occurredAt) {}
