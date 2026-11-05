package com.meloo.catalog.api;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Full replacement of the editable track metadata: genre, country and release year (the decade is
 * derived from the release year). A null value clears the field.
 */
public record TrackMetadataUpdate(
    @Nullable UUID genreId, @Nullable String countryCode, @Nullable Short releaseYear) {}
