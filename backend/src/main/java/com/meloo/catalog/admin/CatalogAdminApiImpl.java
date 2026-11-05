package com.meloo.catalog.admin;

import com.meloo.catalog.api.AdminChange;
import com.meloo.catalog.api.AdminImportResult;
import com.meloo.catalog.api.AdminPage;
import com.meloo.catalog.api.AdminPlaylistView;
import com.meloo.catalog.api.AdminTrackQuery;
import com.meloo.catalog.api.AdminTrackView;
import com.meloo.catalog.api.CatalogAdminApi;
import com.meloo.catalog.api.CatalogAdminException;
import com.meloo.catalog.api.CatalogAdminException.Reason;
import com.meloo.catalog.api.TrackMetadataUpdate;
import com.meloo.catalog.importing.CatalogImportService;
import com.meloo.catalog.persistence.Track;
import com.meloo.catalog.provider.MusicProvider;
import com.meloo.catalog.provider.ProviderTrack;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Facade of {@link CatalogAdminApi}. Not transactional itself: provider HTTP calls must stay
 * outside database transactions, so each delegate owns its transaction.
 */
@Service
class CatalogAdminApiImpl implements CatalogAdminApi {

  private static final int MAX_IMPORT_LIMIT = 50;

  private final AdminTrackOperations tracks;
  private final AdminPlaylistOperations playlists;
  private final CatalogImportService importService;
  private final MusicProvider musicProvider;

  CatalogAdminApiImpl(
      AdminTrackOperations tracks,
      AdminPlaylistOperations playlists,
      CatalogImportService importService,
      MusicProvider musicProvider) {
    this.tracks = tracks;
    this.playlists = playlists;
    this.importService = importService;
    this.musicProvider = musicProvider;
  }

  @Override
  public AdminPage<AdminTrackView> searchTracks(AdminTrackQuery query) {
    return tracks.search(query);
  }

  @Override
  public AdminChange<AdminTrackView> setTrackEnabled(
      UUID trackId, boolean enabled, @Nullable String reason) {
    return tracks.setEnabled(trackId, enabled, reason);
  }

  @Override
  public AdminChange<AdminTrackView> updateTrackMetadata(UUID trackId, TrackMetadataUpdate update) {
    return tracks.updateMetadata(trackId, update);
  }

  @Override
  public AdminImportResult importByQuery(
      String query, int limit, @Nullable UUID genreId, @Nullable String defaultCountryCode) {
    if (query == null || query.isBlank()) {
      throw new CatalogAdminException(Reason.INVALID_ARGUMENT, "query must not be blank");
    }
    int bounded = Math.max(1, Math.min(limit, MAX_IMPORT_LIMIT));
    return doImport(musicProvider.search(query.strip(), bounded), genreId, defaultCountryCode);
  }

  @Override
  public AdminImportResult importByProviderTrackId(
      String providerTrackId, @Nullable UUID genreId, @Nullable String defaultCountryCode) {
    if (providerTrackId == null || providerTrackId.isBlank()) {
      throw new CatalogAdminException(Reason.INVALID_ARGUMENT, "providerTrackId must not be blank");
    }
    ProviderTrack found =
        musicProvider
            .getTrack(providerTrackId.strip())
            .orElseThrow(
                () ->
                    new CatalogAdminException(
                        Reason.NOT_FOUND, "Provider track not found: " + providerTrackId));
    return doImport(List.of(found), genreId, defaultCountryCode);
  }

  private AdminImportResult doImport(
      List<ProviderTrack> found, @Nullable UUID genreId, @Nullable String defaultCountryCode) {
    CatalogImportService.ImportResult result =
        importService.importTracks(found, genreId, defaultCountryCode);
    List<UUID> ids = result.importedTracks().stream().map(Track::getId).toList();
    List<AdminTrackView> views = tracks.getViews(ids);
    return new AdminImportResult(
        result.totalProcessed(),
        result.importedCount(),
        result.skippedNoPreviewCount(),
        result.duplicateCount(),
        views);
  }

  @Override
  public List<AdminPlaylistView> listPlaylists() {
    return playlists.list();
  }

  @Override
  public AdminChange<AdminPlaylistView> createPlaylist(
      String code, String nameVi, String nameEn, List<UUID> trackIds) {
    return playlists.create(code, nameVi, nameEn, trackIds);
  }

  @Override
  public AdminChange<AdminPlaylistView> updatePlaylist(
      UUID playlistId, String nameVi, String nameEn, boolean enabled) {
    return playlists.update(playlistId, nameVi, nameEn, enabled);
  }

  @Override
  public AdminChange<AdminPlaylistView> addTracksToPlaylist(
      UUID playlistId, Collection<UUID> trackIds) {
    return playlists.addTracks(playlistId, trackIds);
  }

  @Override
  public AdminChange<AdminPlaylistView> removeTracksFromPlaylist(
      UUID playlistId, Collection<UUID> trackIds) {
    return playlists.removeTracks(playlistId, trackIds);
  }

  @Override
  public AdminChange<AdminPlaylistView> deletePlaylist(UUID playlistId) {
    return playlists.delete(playlistId);
  }

  @Override
  public int minPlaylistTracks() {
    return playlists.minTracks();
  }
}
