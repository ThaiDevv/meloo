package com.meloo.admin.web;

import com.meloo.admin.analytics.DashboardService;
import com.meloo.admin.audit.AuditQueries;
import com.meloo.admin.catalog.AdminCatalogService;
import com.meloo.admin.daily.AdminDailyService;
import com.meloo.admin.moderation.AdminModerationService;
import com.meloo.catalog.api.AdminPlaylistView;
import com.meloo.catalog.api.AdminTrackQuery;
import com.meloo.catalog.api.AdminTrackView;
import com.meloo.catalog.api.CatalogAdminApi;
import com.meloo.catalog.api.TrackMetadataUpdate;
import com.meloo.chat.api.ChatAdminApi;
import com.meloo.chat.api.ReportStatus;
import com.meloo.generated.api.AdminApi;
import com.meloo.generated.model.*;
import com.meloo.identity.api.UserAccounts;
import com.meloo.progression.api.RankPointsQuery;
import com.meloo.shared.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("@adminAccess.allowed()")
public class AdminController implements AdminApi {
  private final AdminModerationService moderation;
  private final AdminDailyService daily;
  private final ChatAdminApi chat;
  private final UserAccounts users;
  private final RankPointsQuery ranks;
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
      AuditQueries audit,
      AdminModerationService moderation,
      AdminDailyService daily,
      ChatAdminApi chat,
      UserAccounts users,
      RankPointsQuery ranks) {
    this.moderation = moderation;
    this.daily = daily;
    this.chat = chat;
    this.users = users;
    this.ranks = ranks;
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
  public ResponseEntity<AdminAnalyticsDto> getAdminAnalytics(Integer days) {
    return ResponseEntity.ok(dashboard.analytics(days));
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

  @Override
  public ResponseEntity<AdminUserDto> getAdminUser(UUID id) {
    var user = users.get(id);
    return ResponseEntity.ok(
        ModerationViews.user(user, ranks.findAll(java.util.List.of(id)).get(id)));
  }

  @Override
  public ResponseEntity<AdminUserPageDto> listAdminUsers(
      @Nullable String q,
      @Nullable UserKindDto kind,
      @Nullable UserRoleDto role,
      @Nullable UserAccountStatusDto status,
      Integer page,
      Integer size) {
    if (kind != null || role != null || status != null) {
      throw new ApiException(
          HttpStatus.NOT_IMPLEMENTED,
          "NOT_IMPLEMENTED",
          "Identity API does not support kind, role or status filters yet");
    }
    var result = users.search(q == null ? "" : q, page, size);
    var points =
        ranks.findAll(result.items().stream().map(com.meloo.identity.api.UserAccount::id).toList());
    return ResponseEntity.ok(
        new AdminUserPageDto()
            .page(page)
            .size(size)
            .totalItems(result.total())
            .totalPages(Math.toIntExact((result.total() + size - 1) / size))
            .items(
                result.items().stream()
                    .map(user -> ModerationViews.user(user, points.get(user.id())))
                    .toList()));
  }

  @Override
  public ResponseEntity<AdminUserDto> updateAdminUserLock(
      UUID id, AdminUserLockRequestDto request) {
    var user = moderation.lock(access.actor(), id, request.getLocked(), request.getReason());
    return ResponseEntity.ok(
        ModerationViews.user(user, ranks.findAll(java.util.List.of(id)).get(id)));
  }

  @Override
  public ResponseEntity<AdminUserDto> updateAdminUserRole(
      UUID id, AdminUserRoleRequestDto request) {
    var user = moderation.role(access.actor(), id, request.getRole().getValue());
    return ResponseEntity.ok(
        ModerationViews.user(user, ranks.findAll(java.util.List.of(id)).get(id)));
  }

  @Override
  public ResponseEntity<AdminChatReportPageDto> listAdminChatReports(
      ChatReportStatusDto status, Integer page, Integer size) {
    var reports = chat.listReports(ReportStatus.valueOf(status.getValue()));
    return ResponseEntity.ok(
        new AdminChatReportPageDto()
            .page(page)
            .size(size)
            .totalItems((long) reports.size())
            .totalPages((reports.size() + size - 1) / size)
            .items(
                reports.stream()
                    .skip((long) page * size)
                    .limit(size)
                    .map(ModerationViews::report)
                    .toList()));
  }

  @Override
  public ResponseEntity<AdminChatReportDto> resolveAdminChatReport(
      UUID id, AdminResolveChatReportRequestDto request) {
    return ResponseEntity.ok(
        ModerationViews.report(
            moderation.resolve(
                access.actor(),
                id,
                ReportStatus.valueOf(request.getStatus().getValue()),
                Boolean.TRUE.equals(request.getLockSender()),
                request.getLockReason())));
  }

  @Override
  public ResponseEntity<AdminBannedWordListResponseDto> listAdminBannedWords() {
    return ResponseEntity.ok(
        new AdminBannedWordListResponseDto(
            chat.listBannedWords().stream().map(ModerationViews::word).toList()));
  }

  @Override
  public ResponseEntity<AdminBannedWordDto> createAdminBannedWord(
      AdminBannedWordRequestDto request) {
    return ResponseEntity.status(201)
        .body(ModerationViews.word(moderation.word(access.actor(), null, request.getWord())));
  }

  @Override
  public ResponseEntity<AdminBannedWordDto> updateAdminBannedWord(
      UUID id, AdminBannedWordRequestDto request) {
    return ResponseEntity.ok(
        ModerationViews.word(moderation.word(access.actor(), id, request.getWord())));
  }

  @Override
  public ResponseEntity<Void> deleteAdminBannedWord(UUID id) {
    moderation.deleteWord(access.actor(), id);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<AdminDailyScheduleResponseDto> listAdminDailySchedule(
      @Nullable LocalDate from, @Nullable LocalDate to) {
    return ResponseEntity.ok(new AdminDailyScheduleResponseDto(daily.list(from, to)));
  }

  @Override
  public ResponseEntity<AdminDailyScheduleItemDto> scheduleAdminDailyChallenge(
      LocalDate date, AdminScheduleDailyRequestDto request) {
    return ResponseEntity.ok(daily.schedule(access.actor(), date, request.getTrackId()));
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

  // The generated interface uses AOP @Validated, so out-of-range query values (e.g. days) arrive as
  // ConstraintViolationException instead of MVC validation errors.
  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ProblemDetail> invalidParameters(HttpServletRequest request) {
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request parameters");
    problem.setTitle("Validation failed");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", "VALIDATION_FAILED");
    return ResponseEntity.badRequest().body(problem);
  }
}
