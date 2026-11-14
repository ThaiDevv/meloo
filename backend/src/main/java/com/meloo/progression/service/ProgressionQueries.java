package com.meloo.progression.service;

import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.generated.model.*;
import com.meloo.identity.api.UserAccounts;
import com.meloo.shared.api.ApiException;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProgressionQueries {
  private static final String HISTORY =
      """
      select distinct on (mp.match_id) mp.id as player_id, mp.total_score, mp.correct_count,
          mp.rank, mp.rank_delta, m.*,
          (select count(*) from match_player p where p.match_id = m.id) as player_count
      from match_player mp join match m on m.id = mp.match_id
      where mp.owner_id = ?
      """;
  private static final String HISTORY_ORDER =
      " order by mp.match_id, (mp.user_id = mp.owner_id) desc, mp.id";
  private final JdbcTemplate jdbc;
  private final UserAccounts accounts;
  private final CatalogService catalog;

  public ProgressionQueries(JdbcTemplate jdbc, UserAccounts accounts, CatalogService catalog) {
    this.jdbc = jdbc;
    this.accounts = accounts;
    this.catalog = catalog;
  }

  public MatchHistoryPageDto history(UUID owner, int page, int size) {
    validatePage(page, size);
    long total =
        jdbc.queryForObject(
            "select count(distinct match_id) from match_player where owner_id = ?",
            Long.class,
            owner);
    var rows =
        jdbc.query(
            "select * from ("
                + HISTORY
                + HISTORY_ORDER
                + ") history order by ended_at desc, id limit ? offset ?",
            (rs, row) -> historyItem(rs),
            owner,
            size,
            (long) page * size);
    return new MatchHistoryPageDto(page, size, total, pages(total, size), rows);
  }

  public MatchHistoryDetailDto detail(UUID owner, UUID matchId) {
    var rows =
        jdbc.query(
            HISTORY + " and m.id = ?" + HISTORY_ORDER,
            (rs, row) -> new History(rs.getObject("player_id", UUID.class), historyItem(rs)),
            owner,
            matchId);
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Match not found");
    }
    History history = rows.getFirst();
    var item = history.item();
    List<MatchHistoryPlayerDto> players =
        jdbc.query(
            """
        select * from match_player where match_id = ? order by rank, id
        """,
            (rs, row) ->
                new MatchHistoryPlayerDto(
                        rs.getString("nickname"),
                        rs.getBoolean("is_guest"),
                        rs.getInt("rank"),
                        rs.getInt("total_score"),
                        rs.getInt("correct_count"),
                        rs.getLong("correct_time_ms"))
                    .userId(rs.getObject("user_id", UUID.class))
                    .rankDelta(rs.getObject("rank_delta", Integer.class)),
            matchId);
    var roundRows =
        jdbc.queryForList(
            """
        select r.*, rr.outcome, rr.correct_level, rr.base_score, rr.combo_percent, rr.speed_bonus,
            rr.round_score, rr.wrong_count, rr.answer_time_ms from round_result rr
        join round r on r.id = rr.round_id where rr.match_player_id = ? order by r.round_no
        """,
            history.playerId());
    // Historical source snapshots are authoritative. Only pre-V11 rows need catalog resolution.
    var legacyIds =
        roundRows.stream()
            .filter(r -> r.get("provider") == null || r.get("provider_track_id") == null)
            .map(r -> (UUID) r.get("track_id"))
            .toList();
    Map<UUID, TrackInfo> legacy =
        legacyIds.isEmpty() ? Map.of() : catalog.getTracksByIds(legacyIds);
    var rounds = roundRows.stream().map(r -> round(r, legacy)).toList();
    return new MatchHistoryDetailDto()
        .matchId(item.getMatchId())
        .type(item.getType())
        .mode(item.getMode())
        .ranked(item.getRanked())
        .status(item.getStatus())
        .roundCount(MatchHistoryDetailDto.RoundCountEnum.fromValue(item.getRoundCount().getValue()))
        .totalScore(item.getTotalScore())
        .correctCount(item.getCorrectCount())
        .rank(item.getRank())
        .playerCount(item.getPlayerCount())
        .rankDelta(item.getRankDelta())
        .playlistId(item.getPlaylistId())
        .startedAt(item.getStartedAt())
        .endedAt(item.getEndedAt())
        .players(players)
        .rounds(rounds);
  }

  public PlayerStatsDto stats(UUID owner) {
    var rows =
        jdbc.query(
            "select * from player_stats where user_id = ?",
            (rs, row) -> {
              int played = rs.getInt("matches_played"), won = rs.getInt("matches_won");
              int correct = rs.getInt("rounds_correct");
              return new PlayerStatsDto(
                      played,
                      won,
                      played == 0 ? 0d : 100d * won / played,
                      rs.getInt("rounds_played"),
                      correct,
                      rs.getInt("best_combo"),
                      Math.toIntExact(rs.getLong("ranked_score")),
                      rs.getInt("xp"))
                  .averageCorrectLevel(
                      correct == 0 ? null : (double) rs.getLong("correct_level_total") / correct)
                  .averageListenSeconds(
                      correct == 0 ? null : (double) rs.getLong("listen_seconds_total") / correct);
            },
            owner);
    return rows.isEmpty() ? new PlayerStatsDto(0, 0, 0d, 0, 0, 0, 0, 0) : rows.getFirst();
  }

  public RankLeaderboardPageDto leaderboard(UUID caller, int page, int size) {
    validatePage(page, size);
    // Eligibility must be resolved before positions/pagination, including locked/merged accounts.
    // Work is proportional to ranked players, never to match/round history (100k+ results).
    var candidates =
        jdbc.query(
            """
        select user_id, ranked_score, matches_played, matches_won, ranked_reached_at
        from player_stats where not is_guest and ranked_matches > 0
        order by ranked_score desc, ranked_reached_at, user_id
        """,
            (rs, row) ->
                new Candidate(
                    rs.getObject("user_id", UUID.class),
                    Math.toIntExact(rs.getLong("ranked_score")),
                    rs.getInt("matches_played"),
                    rs.getInt("matches_won"),
                    rs.getTimestamp("ranked_reached_at").toInstant().atOffset(ZoneOffset.UTC)));
    var users = accounts.findByIdIn(candidates.stream().map(Candidate::id).toList());
    List<RankLeaderboardEntryDto> items = new ArrayList<>();
    RankLeaderboardEntryDto mine = null;
    long offset = (long) page * size;
    int rank = 0;
    for (var candidate : candidates) {
      var user = users.get(candidate.id());
      if (user == null) {
        continue;
      }
      rank++;
      if ((rank > offset && rank <= offset + size) || candidate.id().equals(caller)) {
        var entry =
            new RankLeaderboardEntryDto(
                rank,
                candidate.id(),
                user.nickname(),
                candidate.points(),
                candidate.played(),
                candidate.won(),
                candidate.reachedAt());
        entry.setAvatarId(
            user.avatarId() == null
                ? null
                : RankLeaderboardEntryDto.AvatarIdEnum.fromValue(user.avatarId()));
        if (rank > offset && rank <= offset + size) {
          items.add(entry);
        }
        if (candidate.id().equals(caller)) {
          mine = entry;
        }
      }
    }
    return new RankLeaderboardPageDto(page, size, (long) rank, pages(rank, size), items)
        .myEntry(mine);
  }

  private static MatchHistoryItemDto historyItem(ResultSet rs) throws SQLException {
    String mode = rs.getString("mode");
    boolean solo = "SOLO".equals(rs.getString("type"));
    return new MatchHistoryItemDto()
        .matchId(rs.getObject("id", UUID.class))
        .type(MatchTypeDto.valueOf(rs.getString("type")))
        .mode(solo ? SoloModeDto.fromValue(mode == null ? "PRACTICE" : mode) : null)
        .ranked(rs.getBoolean("ranked"))
        .status(MatchStatusDto.valueOf(rs.getString("status")))
        .roundCount(MatchHistoryItemDto.RoundCountEnum.fromValue(rs.getInt("round_count")))
        .totalScore(rs.getInt("total_score"))
        .correctCount(rs.getInt("correct_count"))
        .rank(rs.getInt("rank"))
        .playerCount(rs.getInt("player_count"))
        .rankDelta(rs.getObject("rank_delta", Integer.class))
        .playlistId(rs.getObject("playlist_id", UUID.class))
        .startedAt(rs.getTimestamp("started_at").toInstant().atOffset(ZoneOffset.UTC))
        .endedAt(rs.getTimestamp("ended_at").toInstant().atOffset(ZoneOffset.UTC));
  }

  private static MatchHistoryRoundDto round(Map<String, Object> row, Map<UUID, TrackInfo> legacy) {
    UUID trackId = (UUID) row.get("track_id");
    String provider = (String) row.get("provider");
    String providerId = (String) row.get("provider_track_id");
    if (provider == null || providerId == null) {
      TrackInfo track = legacy.get(trackId);
      if (track == null) {
        throw new ApiException(
            HttpStatus.NOT_FOUND, "NOT_FOUND", "Historical track source unavailable");
      }
      provider = track.provider();
      providerId = track.providerTrackId();
    }
    String prefix =
        "ITUNES".equals(provider)
            ? "https://music.apple.com/song/"
            : "https://www.deezer.com/track/";
    AnswerDto answer =
        new AnswerDto(
            trackId,
            (String) row.get("track_title"),
            (String) row.get("artist_name"),
            MusicProviderDto.valueOf(provider),
            URI.create(prefix + providerId));
    Number level = (Number) row.get("correct_level");
    return new MatchHistoryRoundDto(
            number(row, "round_no"),
            RoundOutcomeDto.valueOf((String) row.get("outcome")),
            MatchHistoryRoundDto.BaseScoreEnum.fromValue(number(row, "base_score")),
            MatchHistoryRoundDto.ComboPercentEnum.fromValue(number(row, "combo_percent")),
            number(row, "speed_bonus"),
            number(row, "round_score"),
            number(row, "wrong_count"),
            answer)
        .correctLevel(level == null ? null : level.intValue())
        .listenSeconds(
            level == null
                ? null
                : DailyListenSecondsDto.fromValue(
                    ProgressionWriter.listenSeconds(level.intValue())))
        .answerTimeMs(row.get("answer_time_ms") == null ? null : number(row, "answer_time_ms"));
  }

  private static int number(Map<String, Object> row, String name) {
    return ((Number) row.get(name)).intValue();
  }

  private static int pages(long total, int size) {
    return Math.toIntExact((total + size - 1) / size);
  }

  private static void validatePage(int page, int size) {
    if (page < 0 || size < 1 || size > 50) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid page or size");
    }
  }

  private record History(UUID playerId, MatchHistoryItemDto item) {}

  private record Candidate(UUID id, int points, int played, int won, OffsetDateTime reachedAt) {}
}
