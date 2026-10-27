package com.meloo.catalog.api;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Filter criteria for selecting random tracks in single play or room matches.
 *
 * <p>Values inside one list are OR-ed; the lists are AND-ed. An empty or null list does not filter.
 */
public record TrackFilter(
    @Nullable List<String> genreCodes,
    @Nullable List<String> countryCodes,
    @Nullable List<Integer> decades,
    @Nullable List<UUID> artistIds) {

  public boolean isEmpty() {
    return (genreCodes == null || genreCodes.isEmpty())
        && (countryCodes == null || countryCodes.isEmpty())
        && (decades == null || decades.isEmpty())
        && (artistIds == null || artistIds.isEmpty());
  }
}
