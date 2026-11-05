package com.meloo.catalog.api;

import org.jspecify.annotations.Nullable;

/**
 * Filter for the admin track list. All fields are optional and AND-ed. {@code enabled == null}
 * returns both enabled and disabled tracks.
 *
 * @param text matched against normalized title and artist name
 * @param decade e.g. 1990
 */
public record AdminTrackQuery(
    @Nullable String text,
    @Nullable Boolean enabled,
    @Nullable String genreCode,
    @Nullable String countryCode,
    @Nullable Integer decade,
    int page,
    int size) {}
