package com.meloo.identity.events;

import java.util.UUID;

/** Contains no personal data; repeated deliveries must be safe for each owning module. */
public record AccountDeleted(UUID userId) {}
