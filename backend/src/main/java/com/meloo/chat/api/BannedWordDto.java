package com.meloo.chat.api;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record BannedWordDto(UUID id, String word, @Nullable UUID createdBy, Instant createdAt) {}
