package com.meloo.admin.analytics;

import com.meloo.admin.config.AdminProperties;
import com.meloo.identity.api.UserAccounts;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** KPIs for the admin dashboard and analytics API. Only retained events contribute. */
@Service
@Transactional(readOnly = true)
public class AnalyticsQueries {
  public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

  // Resolve identities at query time: merge and match publications may be delivered out of order.
  private static final String PLAYER_DAYS =
      """
      with recursive merges as (
        select distinct user_id as guest, (props ->> 'userId')::uuid as target
        from analytics_event where event_type = 'GUEST_MERGED'
      ), activity as (
        select p.value::uuid as player, (e.occurred_at at time zone 'Asia/Ho_Chi_Minh')::date as day,
               e.event_type = 'MATCH_FINISHED' as completed
        from analytics_event e cross join lateral jsonb_array_elements_text(e.props -> 'players') p
        where e.event_type in ('MATCH_STARTED', 'MATCH_FINISHED')
        union all
        select user_id, (occurred_at at time zone 'Asia/Ho_Chi_Minh')::date, true
        from analytics_event where event_type = 'DAILY_ATTEMPT_FINISHED' and user_id is not null
      ), resolved(origin, player, path) as (
        select player, player, array[player] from (
          select player from activity union select guest from merges union select target from merges
        ) identities
        union all
        select r.origin, m.target, r.path || m.target from resolved r
        join merges m on r.player = m.guest where not m.target = any(r.path)
      ), canonical as (
        select distinct on (origin) origin, player from resolved
        order by origin, cardinality(path) desc, player
      ), days as (
        select c.player, a.day, bool_or(a.completed) as completed
        from activity a join canonical c on c.origin = a.player
        where c.player not in (:excluded)
          and c.player not in (select player from canonical where origin in (:excluded))
        group by c.player, a.day
      )
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final UserAccounts users;
  private final AdminProperties properties;
  private final Clock clock;

  public AnalyticsQueries(
      NamedParameterJdbcTemplate jdbc,
      UserAccounts users,
      AdminProperties properties,
      Clock clock) {
    this.jdbc = jdbc;
    this.users = users;
    this.properties = properties;
    this.clock = clock;
  }

  /** DAU/WAU count distinct players starting or completing a game, including guests. */
  public Snapshot snapshot() {
    LocalDate today = LocalDate.now(clock.withZone(VIETNAM));
    var params =
        new MapSqlParameterSource("excluded", excludedPlayers())
            .addValue("today", today)
            .addValue("weekStart", today.minusDays(6));
    long dau =
        count(PLAYER_DAYS + "select count(distinct player) from days where day = :today", params);
    long wau =
        count(
            PLAYER_DAYS
                + "select count(distinct player) from days where day between :weekStart and :today",
            params);
    ReturningPlayers returning = null;
    if (properties.betaStart() != null) {
      params
          .addValue("start", properties.betaStart())
          .addValue("end", properties.betaEnd())
          .addValue("cutoff", properties.betaCutoff());
      returning =
          jdbc.queryForObject(
              PLAYER_DAYS
                  + """
          , cohort as (
            select player, min(day) as first_day,
                   count(*) filter (where day between :start and :end) as played_days
            from days where completed group by player
          )
          select count(*) as denominator, count(*) filter (where played_days >= 2) as numerator
          from cohort where first_day between :start and :cutoff
          """,
              params,
              (rs, i) -> new ReturningPlayers(rs.getLong("numerator"), rs.getLong("denominator")));
    }
    var matches =
        jdbc.query(
            """
        select (occurred_at at time zone 'Asia/Ho_Chi_Minh')::date as day, count(*) as total
        from analytics_event where event_type = 'MATCH_FINISHED' and props ->> 'status' = 'FINISHED'
        group by day order by day
        """,
            (rs, i) ->
                new MatchesPerDay(rs.getObject("day", LocalDate.class), rs.getLong("total")));
    var popular =
        jdbc.query(
            """
        select (props ->> 'playlistId')::uuid as playlist_id, count(*) as total
        from analytics_event where event_type = 'MATCH_FINISHED' and props ->> 'status' = 'FINISHED'
          and props ->> 'playlistId' is not null
        group by playlist_id order by total desc, playlist_id limit 10
        """,
            (rs, i) ->
                new PopularPlaylist(rs.getObject("playlist_id", UUID.class), rs.getLong("total")));
    // Conversion cohort is retained guest creations; a guest merged into an existing account
    // also converted. Count original guest ids so multiple guests merging to one user stay bounded.
    var conversion =
        jdbc.queryForObject(
            """
        select count(distinct g.user_id) as denominator,
               count(distinct g.user_id) filter (where m.user_id is not null) as numerator
        from analytics_event g left join analytics_event m
          on m.event_type = 'GUEST_MERGED' and m.user_id = g.user_id
        where g.event_type = 'GUEST_CREATED' and g.user_id not in (:excluded)
          and (m.props ->> 'userId' is null or (m.props ->> 'userId')::uuid not in (:excluded))
        """,
            params,
            (rs, i) -> new ReturningPlayers(rs.getLong("numerator"), rs.getLong("denominator")));
    return new Snapshot(dau, wau, matches, conversion, returning, popular);
  }

  /**
   * One row per Vietnam day in [from, to] with active and returning players and finished matches.
   * Days without activity are zero-filled so charts keep a continuous axis.
   */
  public List<DailyActivity> daily(LocalDate from, LocalDate to) {
    var params =
        new MapSqlParameterSource("excluded", excludedPlayers())
            .addValue("from", from)
            .addValue("to", to);
    var players = new HashMap<LocalDate, long[]>();
    jdbc.query(
        PLAYER_DAYS
            + """
        , firsts as (select player, min(day) as first_day from days group by player)
        select d.day, count(*) as active, count(*) filter (where f.first_day < d.day) as returning
        from days d join firsts f on f.player = d.player
        where d.day between :from and :to group by d.day
        """,
        params,
        rs -> {
          players.put(
              rs.getObject("day", LocalDate.class),
              new long[] {rs.getLong("active"), rs.getLong("returning")});
        });
    var matches = new HashMap<LocalDate, Long>();
    jdbc.query(
        """
        select (occurred_at at time zone 'Asia/Ho_Chi_Minh')::date as day, count(*) as total
        from analytics_event where event_type = 'MATCH_FINISHED' and props ->> 'status' = 'FINISHED'
          and occurred_at >= :fromInstant and occurred_at < :toInstant
        group by day
        """,
        new MapSqlParameterSource(
                "fromInstant", Timestamp.from(from.atStartOfDay(VIETNAM).toInstant()))
            .addValue(
                "toInstant", Timestamp.from(to.plusDays(1).atStartOfDay(VIETNAM).toInstant())),
        rs -> {
          matches.put(rs.getObject("day", LocalDate.class), rs.getLong("total"));
        });
    var result = new ArrayList<DailyActivity>();
    for (var day = from; !day.isAfter(to); day = day.plusDays(1)) {
      var p = players.getOrDefault(day, new long[2]);
      result.add(new DailyActivity(day, p[0], p[1], matches.getOrDefault(day, 0L)));
    }
    return result;
  }

  /** Admins and configured accounts never count as players. */
  private Set<UUID> excludedPlayers() {
    var excluded = new HashSet<>(properties.excludedUserIds());
    // Public identity API is paged, rather than doing one get() per player or reading its tables.
    for (int page = 0; ; page++) {
      var batch = users.search("", page, 100);
      batch.items().stream()
          .filter(u -> "ADMIN".equals(u.role()))
          .forEach(u -> excluded.add(u.id()));
      if ((long) (page + 1) * 100 >= batch.total()) break;
    }
    // Keep NOT IN well formed when the configured exclusion set is empty.
    if (excluded.isEmpty()) excluded.add(new UUID(0, 0));
    return excluded;
  }

  public int matchesToday() {
    return Math.toIntExact(
        count(
            """
        select count(*) from analytics_event where event_type = 'MATCH_FINISHED'
          and props ->> 'status' = 'FINISHED' and occurred_at >= :from and occurred_at < :to
        """,
            today()));
  }

  public int dailyPlayersToday() {
    // BE-ADM-02 will add the listener; zero is accurate until daily events are ingested.
    return Math.toIntExact(
        count(
            """
        select count(distinct user_id) from analytics_event where event_type = 'DAILY_ATTEMPT_FINISHED'
          and occurred_at >= :from and occurred_at < :to
        """,
            today()));
  }

  private MapSqlParameterSource today() {
    LocalDate today = LocalDate.now(clock.withZone(VIETNAM));
    return new MapSqlParameterSource(
            "from", Timestamp.from(today.atStartOfDay(VIETNAM).toInstant()))
        .addValue("to", Timestamp.from(today.plusDays(1).atStartOfDay(VIETNAM).toInstant()));
  }

  private long count(String sql, MapSqlParameterSource params) {
    Long value = jdbc.queryForObject(sql, params, Long.class);
    return value == null ? 0 : value;
  }

  public record MatchesPerDay(LocalDate day, long matches) {}

  public record DailyActivity(
      LocalDate day, long activePlayers, long returningPlayers, long matches) {}

  public record PopularPlaylist(UUID playlistId, long matches) {}

  public record ReturningPlayers(long numerator, long denominator) {
    /** Fraction in [0,1], unavailable when there are no eligible players. */
    public @Nullable BigDecimal rate() {
      return denominator == 0
          ? null
          : BigDecimal.valueOf(numerator)
              .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }
  }

  public record Snapshot(
      long dau,
      long wau,
      List<MatchesPerDay> matchesPerDay,
      ReturningPlayers guestConversion,
      @Nullable ReturningPlayers returningPlayers,
      List<PopularPlaylist> popularPlaylists) {}
}
