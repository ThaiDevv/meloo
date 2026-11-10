package com.meloo.daily.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Published inside the completion transaction; eventId equals attemptId for deduplication. */
public record DailyAttemptFinished(
    UUID eventId,
    UUID attemptId,
    UUID userId,
    boolean isGuest,
    LocalDate challengeDate,
    String status,
    String outcome,
    @Nullable Integer correctLevel,
    int score,
    int elapsedMs,
    Instant finishedAt) {}
