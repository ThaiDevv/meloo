package com.meloo.catalog.api;

import java.util.List;
import java.util.UUID;

/**
 * Admin view of a playlist.
 *
 * @param trackIds track ids in play order (enabled and disabled)
 * @param enabledTrackCount tracks that can still be chosen for a new match
 */
public record AdminPlaylistView(
    UUID id,
    String code,
    String nameVi,
    String nameEn,
    boolean curated,
    boolean enabled,
    List<UUID> trackIds,
    int enabledTrackCount) {}
