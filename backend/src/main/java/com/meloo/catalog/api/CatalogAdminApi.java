package com.meloo.catalog.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Catalog management for the admin module. There is no REST endpoint here; BE-ADM-01 exposes it.
 *
 * <p>Every write returns an {@link AdminChange} (before and after) so the caller writes the audit
 * log (BR-28). Failures throw {@link CatalogAdminException}.
 */
public interface CatalogAdminApi {

  /** Lists tracks of the catalog, enabled or not, with filters and paging. */
  AdminPage<AdminTrackView> searchTracks(AdminTrackQuery query);

  /**
   * Enables or disables a track (BR-27). A disabled track is never chosen for a new match.
   *
   * @param reason reason shown to admins when disabling; ignored when enabling
   */
  AdminChange<AdminTrackView> setTrackEnabled(
      UUID trackId, boolean enabled, @Nullable String reason);

  /** Replaces genre, country and release year of a track. */
  AdminChange<AdminTrackView> updateTrackMetadata(UUID trackId, TrackMetadataUpdate update);

  /** Imports the provider search results for {@code query}. */
  AdminImportResult importByQuery(
      String query, int limit, @Nullable UUID genreId, @Nullable String defaultCountryCode);

  /** Imports one provider track by its provider id. */
  AdminImportResult importByProviderTrackId(
      String providerTrackId, @Nullable UUID genreId, @Nullable String defaultCountryCode);

  /** Lists curated playlists first, then standard ones. */
  List<AdminPlaylistView> listPlaylists();

  /**
   * Creates a curated playlist. It needs at least {@link #minPlaylistTracks()} valid (existing and
   * enabled) tracks (BR-09).
   */
  AdminChange<AdminPlaylistView> createPlaylist(
      String code, String nameVi, String nameEn, List<UUID> trackIds);

  /** Renames a playlist and/or enables or disables it. Enabling needs enough valid tracks. */
  AdminChange<AdminPlaylistView> updatePlaylist(
      UUID playlistId, String nameVi, String nameEn, boolean enabled);

  /** Adds tracks to a playlist. Only existing, enabled tracks are accepted. */
  AdminChange<AdminPlaylistView> addTracksToPlaylist(UUID playlistId, Collection<UUID> trackIds);

  /** Removes tracks from a playlist; refused if an enabled playlist would become too small. */
  AdminChange<AdminPlaylistView> removeTracksFromPlaylist(
      UUID playlistId, Collection<UUID> trackIds);

  /** Deletes a curated playlist. The standard playlist cannot be deleted. */
  AdminChange<AdminPlaylistView> deletePlaylist(UUID playlistId);

  /** Smallest number of valid tracks an enabled playlist must keep (BR-09). */
  int minPlaylistTracks();
}
