package com.meloo.admin.daily;

import com.meloo.admin.audit.AuditTrail;
import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.daily.api.DailyAdminApi;
import com.meloo.generated.model.*;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminDailyService {
  private final DailyAdminApi daily;
  private final CatalogService catalog;
  private final AuditTrail audit;
  private final Clock clock;

  public AdminDailyService(
      DailyAdminApi daily, CatalogService catalog, AuditTrail audit, Clock clock) {
    this.daily = daily;
    this.catalog = catalog;
    this.audit = audit;
    this.clock = clock;
  }

  public List<AdminDailyScheduleItemDto> list(@Nullable LocalDate from, @Nullable LocalDate to) {
    LocalDate start = from == null ? today() : from;
    var schedule = daily.listSchedule(start, to == null ? start.plusDays(30) : to);
    var tracks =
        catalog.getTracksByIds(
            schedule.stream().map(DailyAdminApi.ScheduleItem::trackId).distinct().toList());
    return schedule.stream().map(item -> view(item, tracks)).toList();
  }

  @Transactional
  public AdminDailyScheduleItemDto schedule(UUID actor, LocalDate date, UUID track) {
    var change = daily.schedule(date, track, actor);
    audit.append(
        actor,
        "DAILY_SCHEDULED",
        "DAILY_CHALLENGE",
        date.toString(),
        null,
        change.before(),
        change.after());
    return view(change.after(), catalog.getTracksByIds(List.of(track)));
  }

  private AdminDailyScheduleItemDto view(
      DailyAdminApi.ScheduleItem item, Map<UUID, TrackInfo> tracks) {
    var track = tracks.get(item.trackId());
    if (track == null)
      throw new com.meloo.shared.api.ApiException(
          org.springframework.http.HttpStatus.NOT_IMPLEMENTED,
          "NOT_IMPLEMENTED",
          "Catalog API cannot provide scheduled track metadata for a disabled track");
    return new AdminDailyScheduleItemDto(
            item.challengeDate(),
            item.trackId(),
            track.title(),
            track.artistName(),
            MusicProviderDto.fromValue(track.provider()),
            DailyChallengeSourceDto.fromValue(item.source()),
            !item.challengeDate().isAfter(today()),
            item.createdAt().atOffset(ZoneOffset.UTC))
        .createdBy(item.setBy());
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
  }
}
