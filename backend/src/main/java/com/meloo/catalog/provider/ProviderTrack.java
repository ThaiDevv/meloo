package com.meloo.catalog.provider;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record ProviderTrack(
    String providerTrackId,
    String title,
    String artistName,
    @Nullable String artistId,
    @Nullable String albumTitle,
    @Nullable Integer durationSeconds,
    @Nullable String previewUrl,
    @Nullable String coverUrl,
    @Nullable Integer popularityRank,
    @Nullable Short releaseYear,
    @Nullable String countryCode) {}
