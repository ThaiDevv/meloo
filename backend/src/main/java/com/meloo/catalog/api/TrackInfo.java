package com.meloo.catalog.api;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Public track details exposed to other application modules (game, room, daily, admin).
 *
 * @param id Meloo internal track UUID
 * @param title Track display title
 * @param artistName Primary artist display name
 * @param provider Name of external music provider (e.g. DEEZER)
 * @param providerTrackId External track ID on the provider
 * @param coverUrl Album cover image URL (may be null)
 * @param genreId Genre ID if assigned
 * @param countryCode ISO-3166-1 alpha-2 country code if assigned
 * @param releaseYear Release year if available
 */
public record TrackInfo(
    UUID id,
    String title,
    String artistName,
    String provider,
    String providerTrackId,
    @Nullable String coverUrl,
    @Nullable UUID genreId,
    @Nullable String countryCode,
    @Nullable Short releaseYear) {}
