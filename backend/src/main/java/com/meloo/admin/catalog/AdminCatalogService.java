package com.meloo.admin.catalog;

import com.meloo.admin.audit.AuditTrail;
import com.meloo.catalog.api.AdminChange;
import com.meloo.catalog.api.AdminImportResult;
import com.meloo.catalog.api.AdminPlaylistView;
import com.meloo.catalog.api.AdminTrackQuery;
import com.meloo.catalog.api.AdminTrackView;
import com.meloo.catalog.api.CatalogAdminApi;
import com.meloo.catalog.api.CatalogAdminException;
import com.meloo.catalog.api.TrackMetadataUpdate;
import com.meloo.generated.model.AdminTrackImportRequestDto;
import com.meloo.shared.api.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdminCatalogService {
  private final CatalogAdminApi catalog;
  private final AuditTrail audit;
  private final TransactionTemplate transaction;

  public AdminCatalogService(
      CatalogAdminApi catalog, AuditTrail audit, PlatformTransactionManager manager) {
    this.catalog = catalog;
    this.audit = audit;
    this.transaction = new TransactionTemplate(manager);
  }

  public AdminTrackView metadata(UUID actor, UUID id, TrackMetadataUpdate update) {
    return change(
            actor,
            "TRACK_METADATA_UPDATED",
            "TRACK",
            id,
            null,
            () -> catalog.updateTrackMetadata(id, update))
        .after();
  }

  public AdminTrackView status(UUID actor, UUID id, boolean enabled, @Nullable String reason) {
    return change(
            actor,
            enabled ? "TRACK_ENABLED" : "TRACK_DISABLED",
            "TRACK",
            id,
            reason,
            () -> catalog.setTrackEnabled(id, enabled, reason))
        .after();
  }

  public AdminPlaylistView create(UUID actor, String code, String vi, String en, List<UUID> ids) {
    return playlistWrite(
        () ->
            transaction.execute(
                status -> {
                  var result = catalog.createPlaylist(code, vi, en, ids);
                  audit.append(
                      actor,
                      "PLAYLIST_CREATED",
                      "PLAYLIST",
                      result.after().id().toString(),
                      null,
                      result.before(),
                      result.after());
                  return result.after();
                }),
        () -> (int) ids.stream().distinct().count());
  }

  public AdminPlaylistView update(UUID actor, UUID id, String vi, String en, boolean enabled) {
    return playlistWrite(
        () ->
            change(
                    actor,
                    "PLAYLIST_UPDATED",
                    "PLAYLIST",
                    id,
                    null,
                    () -> catalog.updatePlaylist(id, vi, en, enabled))
                .after(),
        () -> playlist(id).enabledTrackCount());
  }

  public AdminPlaylistView tracks(UUID actor, UUID id, List<UUID> ids, boolean add) {
    return playlistWrite(
        () ->
            change(
                    actor,
                    add ? "PLAYLIST_TRACKS_ADDED" : "PLAYLIST_TRACKS_REMOVED",
                    "PLAYLIST",
                    id,
                    null,
                    () ->
                        add
                            ? catalog.addTracksToPlaylist(id, ids)
                            : catalog.removeTracksFromPlaylist(id, ids))
                .after(),
        () -> remainingEnabled(id, ids));
  }

  public void delete(UUID actor, UUID id) {
    if (!playlist(id).curated()) {
      throw new ApiException(
          HttpStatus.CONFLICT, "STANDARD_PLAYLIST_PROTECTED", "The standard playlist is protected");
    }
    change(actor, "PLAYLIST_DELETED", "PLAYLIST", id, null, () -> catalog.deletePlaylist(id));
  }

  private AdminPlaylistView playlist(UUID id) {
    return catalog.listPlaylists().stream()
        .filter(p -> p.id().equals(id))
        .findFirst()
        .orElseThrow(
            () ->
                new ApiException(HttpStatus.NOT_FOUND, "PLAYLIST_NOT_FOUND", "Playlist not found"));
  }

  private int remainingEnabled(UUID id, List<UUID> removed) {
    var remaining = new HashSet<>(playlist(id).trackIds());
    remaining.removeAll(removed);
    int enabled = 0;
    // Only the exceptional 422 path needs this fallback: CatalogAdminException has no count.
    // Use the public paged API, never catalog persistence or parsing an exception message.
    for (int page = 0; !remaining.isEmpty(); page++) {
      var batch =
          catalog.searchTracks(new AdminTrackQuery(null, true, null, null, null, page, 100));
      for (var track : batch.items()) if (remaining.remove(track.id())) enabled++;
      if ((long) (page + 1) * 100 >= batch.total()) break;
    }
    return enabled;
  }

  private <T> T playlistWrite(Supplier<T> write, IntSupplier validCount) {
    try {
      return write.get();
    } catch (CatalogAdminException error) {
      if (error.reason() != CatalogAdminException.Reason.PLAYLIST_NOT_ENOUGH_TRACKS) throw error;
      throw new InsufficientPlaylistTracks(
          Math.max(1, catalog.minPlaylistTracks() - validCount.getAsInt()));
    }
  }

  private <T> AdminChange<T> change(
      UUID actor,
      String action,
      String type,
      UUID id,
      @Nullable String reason,
      Supplier<AdminChange<T>> mutation) {
    return transaction.execute(
        status -> {
          var result = mutation.get();
          audit.append(actor, action, type, id.toString(), reason, result.before(), result.after());
          return result;
        });
  }

  /** Provider HTTP must not run inside a database transaction. Persist intent before calling it. */
  @Transactional(propagation = Propagation.NEVER)
  public AdminImportResult importTracks(UUID actor, AdminTrackImportRequestDto request) {
    boolean query = request.getQuery() != null && !request.getQuery().isBlank();
    boolean id = request.getProviderTrackId() != null && !request.getProviderTrackId().isBlank();
    if (query == id || request.getLimit() == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "Exactly one import source and a valid limit are required");
    }
    String operationId = UUID.randomUUID().toString();
    audit.append(actor, "TRACK_IMPORT_REQUESTED", "TRACK_IMPORT", operationId, null, null, request);
    AdminImportResult result;
    try {
      result =
          query
              ? catalog.importByQuery(
                  request.getQuery(),
                  request.getLimit(),
                  request.getGenreId(),
                  request.getDefaultCountryCode())
              : catalog.importByProviderTrackId(
                  request.getProviderTrackId(),
                  request.getGenreId(),
                  request.getDefaultCountryCode());
    } catch (RuntimeException failure) {
      audit.append(
          actor,
          "TRACK_IMPORT_FAILED",
          "TRACK_IMPORT",
          operationId,
          null,
          request,
          Map.of("status", "FAILED"));
      throw failure;
    }
    audit.append(actor, "TRACK_IMPORTED", "TRACK_IMPORT", operationId, null, request, result);
    return result;
  }
}
