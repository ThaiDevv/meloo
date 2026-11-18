package com.meloo.admin.analytics;

import com.meloo.catalog.api.AdminTrackQuery;
import com.meloo.catalog.api.CatalogAdminApi;
import com.meloo.chat.api.ChatAdminApi;
import com.meloo.chat.api.ReportStatus;
import com.meloo.generated.model.AdminAnalyticsDayDto;
import com.meloo.generated.model.AdminAnalyticsDto;
import com.meloo.generated.model.AdminDashboardDto;
import com.meloo.generated.model.AdminRatioDto;
import com.meloo.identity.api.UserAccounts;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
  private final AnalyticsQueries analytics;
  private final CatalogAdminApi catalog;
  private final ChatAdminApi chat;
  private final UserAccounts users;
  private final Clock clock;

  public DashboardService(
      AnalyticsQueries analytics,
      CatalogAdminApi catalog,
      ChatAdminApi chat,
      UserAccounts users,
      Clock clock) {
    this.analytics = analytics;
    this.catalog = catalog;
    this.chat = chat;
    this.users = users;
    this.clock = clock;
  }

  public AdminDashboardDto get() {
    var now = clock.instant();
    var today =
        LocalDate.ofInstant(now, AnalyticsQueries.VIETNAM)
            .atStartOfDay(AnalyticsQueries.VIETNAM)
            .toInstant();
    var tomorrow = today.atZone(AnalyticsQueries.VIETNAM).plusDays(1).toInstant();
    // PO approved temporary zeros: room does not yet expose active-room / online-player metrics.
    return new AdminDashboardDto(
        0,
        0,
        analytics.matchesToday(),
        analytics.dailyPlayersToday(),
        chat.listReports(ReportStatus.OPEN).size(),
        tracks(true),
        tracks(false),
        Math.toIntExact(users.countCreated("REGISTERED", Instant.EPOCH, now.plusNanos(1))),
        Math.toIntExact(users.countCreated("GUEST", today, tomorrow)),
        now.atOffset(ZoneOffset.UTC));
  }

  /** Daily usage over the last {@code days} Vietnam days, today included. */
  public AdminAnalyticsDto analytics(int days) {
    var now = clock.instant();
    var to = LocalDate.ofInstant(now, AnalyticsQueries.VIETNAM);
    var from = to.minusDays(days - 1L);
    var excluded = analytics.excludedPlayers();
    var snapshot = analytics.snapshot(excluded);
    var daily =
        analytics.daily(from, to, excluded).stream()
            .map(
                d ->
                    new AdminAnalyticsDayDto(
                        d.day(),
                        Math.toIntExact(d.activePlayers()),
                        Math.toIntExact(d.returningPlayers()),
                        Math.toIntExact(d.matches())))
            .toList();
    return new AdminAnalyticsDto(
        from,
        to,
        Math.toIntExact(snapshot.dau()),
        Math.toIntExact(snapshot.wau()),
        snapshot.returningPlayers() == null ? null : ratio(snapshot.returningPlayers()),
        ratio(snapshot.guestConversion()),
        daily,
        now.atOffset(ZoneOffset.UTC));
  }

  private static AdminRatioDto ratio(AnalyticsQueries.ReturningPlayers value) {
    return new AdminRatioDto(
        Math.toIntExact(value.numerator()), Math.toIntExact(value.denominator()), value.rate());
  }

  private int tracks(boolean enabled) {
    return Math.toIntExact(
        catalog.searchTracks(new AdminTrackQuery(null, enabled, null, null, null, 0, 1)).total());
  }
}
