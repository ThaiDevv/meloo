package com.meloo.catalog.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Public catalog service consumed by other modules (game, room, daily).
 *
 * <p>Provides methods to select random valid tracks (enabled only, no duplicates) and to retrieve
 * track details including coverUrl for round reveals.
 */
public interface CatalogService {

  /**
   * Selects up to {@code count} random enabled tracks from the specified playlist.
   *
   * @param playlistId playlist UUID
   * @param count number of tracks to select
   * @param excludedTrackIds track IDs to exclude from selection (e.g. tracks already played)
   * @return list of distinct enabled tracks
   */
  List<TrackInfo> selectRandomTracksByPlaylist(
      UUID playlistId, int count, Set<UUID> excludedTrackIds);

  default List<TrackInfo> selectRandomTracksByPlaylist(UUID playlistId, int count) {
    return selectRandomTracksByPlaylist(playlistId, count, Set.of());
  }

  /**
   * Selects up to {@code count} random enabled tracks from the playlist with code {@code
   * playlistCode}.
   *
   * @param playlistCode unique playlist code (e.g. "standard")
   * @param count number of tracks to select
   * @param excludedTrackIds track IDs to exclude from selection
   * @return list of distinct enabled tracks
   */
  List<TrackInfo> selectRandomTracksByPlaylistCode(
      String playlistCode, int count, Set<UUID> excludedTrackIds);

  default List<TrackInfo> selectRandomTracksByPlaylistCode(String playlistCode, int count) {
    return selectRandomTracksByPlaylistCode(playlistCode, count, Set.of());
  }

  /**
   * Selects up to {@code count} random enabled tracks matching the given filter. If {@code filter}
   * is null or empty, selects from the entire catalog.
   *
   * @param filter optional filter criteria (genres, countries, decades, artists)
   * @param count number of tracks to select
   * @param excludedTrackIds track IDs to exclude from selection
   * @return list of distinct enabled tracks
   */
  List<TrackInfo> selectRandomTracks(
      @Nullable TrackFilter filter, int count, Set<UUID> excludedTrackIds);

  default List<TrackInfo> selectRandomTracks(@Nullable TrackFilter filter, int count) {
    return selectRandomTracks(filter, count, Set.of());
  }

  /**
   * Retrieves track information by ID. Returns empty if not found or disabled.
   *
   * @param trackId track UUID
   * @return optional track information
   */
  Optional<TrackInfo> getTrackById(UUID trackId);

  /**
   * Retrieves track information for multiple track IDs.
   *
   * @param trackIds collection of track UUIDs
   * @return map of track ID to TrackInfo
   */
  Map<UUID, TrackInfo> getTracksByIds(Collection<UUID> trackIds);
}
