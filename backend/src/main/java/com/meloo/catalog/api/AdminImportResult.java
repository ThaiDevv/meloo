package com.meloo.catalog.api;

import java.util.List;

/**
 * Result of importing tracks from the music provider.
 *
 * @param tracks tracks created by this call plus the already-known tracks it matched
 */
public record AdminImportResult(
    int totalProcessed,
    int importedCount,
    int skippedNoPreviewCount,
    int duplicateCount,
    List<AdminTrackView> tracks) {}
