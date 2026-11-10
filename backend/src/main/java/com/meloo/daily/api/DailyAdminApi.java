package com.meloo.daily.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The admin module authorizes callers and audits changes; daily owns dates and assignments. */
public interface DailyAdminApi {
  List<ScheduleItem> listSchedule(LocalDate from, LocalDate to);

  ScheduleChange schedule(LocalDate date, UUID trackId, UUID adminId);

  record ScheduleItem(
      LocalDate challengeDate,
      UUID trackId,
      String source,
      @Nullable UUID setBy,
      Instant createdAt) {}

  record ScheduleChange(@Nullable ScheduleItem before, ScheduleItem after) {}
}
