package com.meloo.admin.web;

import com.meloo.admin.analytics.DashboardService;
import com.meloo.admin.audit.AuditQueries;
import com.meloo.admin.catalog.AdminCatalogService;
import com.meloo.catalog.api.AdminPlaylistView;
import com.meloo.catalog.api.AdminTrackQuery;
import com.meloo.catalog.api.AdminTrackView;
import com.meloo.catalog.api.CatalogAdminApi;
import com.meloo.catalog.api.TrackMetadataUpdate;
import com.meloo.generated.api.AdminApi;
import com.meloo.generated.model.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("@adminAccess.allowed()")
public class AdminController implements AdminApi {
  private final AdminAccess access;
  private final CatalogAdminApi catalog;
  private final AdminCatalogService writes;
  private final DashboardService dashboard;
  private final AuditQueries audit;

  public AdminController(
      AdminAccess access,
      CatalogAdminApi catalog,
      AdminCatalogService writes,
      DashboardService dashboard,
      AuditQueries audit) {
    this.access = access;
    this.catalog = catalog;
    this.writes = writes;
    this.dashboard = dashboard;
    this.audit = audit;
  }

  @Override
  public ResponseEntity<AdminDashboardDto> getAdminDashboard() {
    return ResponseEntity.ok(dashboard.get());
  }

  @Override
  public ResponseEntity<AdminTrackPageDto> listAdminTracks(
      @Nullable String q,
      @Nullable Boolean enabled,
      @Nullable String genre,
      @Nullable String country,
      @Nullable Integer decade,
      Integer page,
      Integer size) {
    var result =
        catalog.searchTracks(new AdminTrackQuery(q, enabled, genre, country, decade, page, size));
    return ResponseEntity.ok(
        new AdminTrackPageDto()
            .items(result.items().stream().map(AdminController::track).toList())
            .page(result.page())
            .size(result.size())
            .totalItems(result.total())
            .totalPages(Math.toIntExact((result.total() + result.size() - 1) / result.size())));
  }

  @Override
  public ResponseEntity<AdminTrackDto> updateAdminTrackMetadata(
      UUID id, AdminTrackMetadataRequestDto request) {
    return ResponseEntity.ok(
        track(
            writes.metadata(
                access.actor(),
                id,
                new TrackMetadataUpdate(
                    request.getGenreId(),
                    request.getCountryCode(),
                    request.getReleaseYear() == null
                        ? null
                        : request.getReleaseYear().shortValue()))));
  }

  @Override
  public ResponseEntity<AdminTrackDto> setAdminTrackStatus(
      UUID id, AdminTrackStatusRequestDto request) {
    return ResponseEntity.ok(
        track(
            writes.status(access.actor(), id, request.getEnabled(), request.getDisabledReason())));
  }

  @Override
  public ResponseEntity<AdminImportResultDto> importAdminTracks(
      AdminTrackImportRequestDto request) {
    var result = writes.importTracks(access.actor(), request);
    return ResponseEntity.ok(
        new AdminImportResultDto(
            result.totalProcessed(),
            result.importedCount(),
            result.skippedNoPreviewCount(),
            result.duplicateCount(),
            result.tracks().stream().map(AdminController::track).toList()));
  }

  @Override
  public ResponseEntity<AdminPlaylistListResponseDto> listAdminPlaylists() {
    return ResponseEntity.ok(
        new AdminPlaylistListResponseDto(
            catalog.listPlaylists().stream().map(AdminController::playlist).toList()));
  }

  @Override
  public ResponseEntity<AdminPlaylistDto> createAdminPlaylist(
      AdminCreatePlaylistRequestDto request) {
    return ResponseEntity.status(201)
        .body(
            playlist(
                writes.create(
                    access.actor(),
                    request.getCode(),
                    request.getNameVi(),
                    request.getNameEn(),
                    request.getTrackIds())));
  }

  @Override
  public ResponseEntity<AdminPlaylistDto> updateAdminPlaylist(
      UUID id, AdminUpdatePlaylistRequestDto request) {
    return ResponseEntity.ok(
        playlist(
            writes.update(
                access.actor(),
                id,
                request.getNameVi(),
                request.getNameEn(),
                request.getEnabled())));
  }

  @Override
  public ResponseEntity<AdminPlaylistDto> addAdminPlaylistTracks(
      UUID id, AdminPlaylistTracksRequestDto request) {
    return ResponseEntity.ok(
        playlist(writes.tracks(access.actor(), id, request.getTrackIds(), true)));
  }

  @Override
  public ResponseEntity<AdminPlaylistDto> removeAdminPlaylistTracks(
      UUID id, AdminPlaylistTracksRequestDto request) {
    return ResponseEntity.ok(
        playlist(writes.tracks(access.actor(), id, request.getTrackIds(), false)));
  }

  @Override
  public ResponseEntity<Void> deleteAdminPlaylist(UUID id) {
    writes.delete(access.actor(), id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<AdminAuditLogPageDto> listAdminAuditLogs(
      @Nullable UUID actor,
      @Nullable String action,
      @Nullable String type,
      @Nullable OffsetDateTime from,
      @Nullable OffsetDateTime to,
      Integer page,
      Integer size) {
    return ResponseEntity.ok(audit.list(actor, action, type, from, to, page, size));
  }

  private static AdminTrackDto track(AdminTrackView t) {
    return new AdminTrackDto(
        t.id(),
        MusicProviderDto.fromValue(t.provider()),
        t.providerTrackId(),
        t.title(),
        t.artistName(),
        t.genreId(),
        t.genreCode(),
        t.countryCode(),
        t.releaseYear() == null ? null : t.releaseYear().intValue(),
        t.enabled(),
        t.disabledReason(),
        t.coverUrl());
  }

  private static AdminPlaylistDto playlist(AdminPlaylistView p) {
    return new AdminPlaylistDto(
        p.id(),
        p.code(),
        p.nameVi(),
        p.nameEn(),
        p.curated(),
        p.enabled(),
        p.trackIds(),
        p.enabledTrackCount());
  }
}
