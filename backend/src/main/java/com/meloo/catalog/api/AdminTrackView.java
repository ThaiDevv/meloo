package com.meloo.catalog.api;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Full admin view of a track, including its enabled state. */
public record AdminTrackView(
    UUID id,
    String title,
    String artistName,
    String provider,
    String providerTrackId,
    @Nullable UUID genreId,
    @Nullable String genreCode,
    @Nullable String countryCode,
    @Nullable Short releaseYear,
    boolean enabled,
    @Nullable String disabledReason,
    @Nullable String coverUrl) {}
